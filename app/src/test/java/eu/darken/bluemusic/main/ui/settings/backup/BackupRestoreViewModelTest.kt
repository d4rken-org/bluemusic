package eu.darken.bluemusic.main.ui.settings.backup

import android.net.Uri
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.devices.core.database.DeviceConfigDao
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.devices.core.database.DeviceDatabase
import eu.darken.bluemusic.main.backup.core.AppBackup
import eu.darken.bluemusic.main.backup.core.BackupRestoreManager
import eu.darken.bluemusic.main.backup.core.DeviceConfigBackup
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreViewModelTest : BaseTest() {

    private lateinit var navCtrl: NavigationController
    private lateinit var backupRestoreManager: BackupRestoreManager
    private lateinit var deviceDatabase: DeviceDatabase
    private lateinit var dao: DeviceConfigDao

    private val sampleBackup = AppBackup(
        formatVersion = 1,
        appVersion = "1.0.0",
        createdAt = "2026-01-01T00:00:00Z",
        deviceConfigs = listOf(
            DeviceConfigBackup(address = "AA:AA:AA:AA:AA:AA"),
            DeviceConfigBackup(address = "BB:BB:BB:BB:BB:BB"),
        ),
    )

    @BeforeEach
    fun setup() {
        navCtrl = mockk(relaxed = true)
        backupRestoreManager = mockk(relaxed = true)
        deviceDatabase = mockk(relaxed = true)
        dao = mockk(relaxed = true)
        every { deviceDatabase.devices } returns dao
        coEvery { dao.getAllDevicesOnce() } returns emptyList()
    }

    private fun viewModel(): BackupRestoreViewModel = BackupRestoreViewModel(
        dispatcherProvider = TestDispatcherProvider(UnconfinedTestDispatcher()),
        navCtrl = navCtrl,
        backupRestoreManager = backupRestoreManager,
        deviceDatabase = deviceDatabase,
    )

    @Test
    fun `onBackupClicked emits OpenBackupPicker with dated filename`() = runTest {
        val vm = viewModel()

        val received = mutableListOf<BackupRestoreEvent>()
        val job = launch { vm.events.take(1).toList(received) }

        vm.onBackupClicked()
        advanceUntilIdle()
        job.join()

        val event = received.single() as BackupRestoreEvent.OpenBackupPicker
        event.suggestedName shouldStartWith "bluemusic-backup-"
        event.suggestedName shouldEndWith ".zip"
    }

    @Test
    fun `onRestoreClicked emits OpenRestorePicker`() = runTest {
        val vm = viewModel()

        val received = mutableListOf<BackupRestoreEvent>()
        val job = launch { vm.events.take(1).toList(received) }

        vm.onRestoreClicked()
        advanceUntilIdle()
        job.join()

        received.single() shouldBe BackupRestoreEvent.OpenRestorePicker
    }

    @Test
    fun `onBackupUriSelected populates lastResult and clears isWorking`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        coEvery { backupRestoreManager.createBackup(uri) } returns BackupRestoreManager.BackupResult(deviceConfigCount = 3)
        every { backupRestoreManager.resolveFileName(uri) } returns "export.zip"
        val vm = viewModel()

        vm.onBackupUriSelected(uri)
        advanceUntilIdle()

        val state = vm.state.filterNotNull().first()
        state.isWorking shouldBe false
        state.lastResult?.type shouldBe BackupRestoreViewModel.OperationType.BACKUP
        state.lastResult?.deviceCount shouldBe 3
        state.lastResult?.fileName shouldBe "export.zip"
    }

    @Test
    fun `onRestoreUriSelected populates pendingRestore with overlap count`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        coEvery { backupRestoreManager.parseBackup(uri) } returns BackupRestoreManager.ParseResult(
            backup = sampleBackup,
            versionMismatch = false,
            enumWarnings = listOf("warn"),
        )
        every { backupRestoreManager.resolveFileName(uri) } returns "incoming.zip"
        coEvery { dao.getAllDevicesOnce() } returns listOf(
            DeviceConfigEntity(address = "AA:AA:AA:AA:AA:AA"),
        )
        val vm = viewModel()

        vm.onRestoreUriSelected(uri)
        advanceUntilIdle()

        val preview = vm.state.filterNotNull().first().pendingRestore
        preview.shouldNotBeNull()
        preview.deviceCount shouldBe 2
        preview.existingDeviceCount shouldBe 1
        preview.warnings shouldContain "warn"
        preview.fileName shouldBe "incoming.zip"
    }

    @Test
    fun `onConfirmRestore without pending preview is a no-op`() = runTest {
        val vm = viewModel()

        vm.onConfirmRestore()
        advanceUntilIdle()

        coVerify(exactly = 0) { backupRestoreManager.applyRestore(any(), any()) }
    }

    @Test
    fun `onConfirmRestore applies backup with current skipExisting flag`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        coEvery { backupRestoreManager.parseBackup(uri) } returns BackupRestoreManager.ParseResult(
            backup = sampleBackup,
            versionMismatch = false,
            enumWarnings = emptyList(),
        )
        every { backupRestoreManager.resolveFileName(uri) } returns "r.zip"
        coEvery { backupRestoreManager.applyRestore(any(), any()) } returns BackupRestoreManager.RestoreResult(
            deviceCount = 2,
            skippedCount = 0,
        )
        val vm = viewModel()

        vm.onRestoreUriSelected(uri)
        advanceUntilIdle()
        vm.onToggleSkipExisting(true)
        advanceUntilIdle()
        vm.onConfirmRestore()
        advanceUntilIdle()

        coVerify { backupRestoreManager.applyRestore(sampleBackup, skipExisting = true) }
        val state = vm.state.filterNotNull().first()
        state.pendingRestore shouldBe null
        state.lastResult?.type shouldBe BackupRestoreViewModel.OperationType.RESTORE
        state.lastResult?.deviceCount shouldBe 2
    }

    @Test
    fun `onConfirmRestore preserves pendingRestore when applyRestore throws`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        coEvery { backupRestoreManager.parseBackup(uri) } returns BackupRestoreManager.ParseResult(
            backup = sampleBackup,
            versionMismatch = false,
            enumWarnings = emptyList(),
        )
        every { backupRestoreManager.resolveFileName(uri) } returns "r.zip"
        coEvery { backupRestoreManager.applyRestore(any(), any()) } throws IOException("boom")
        val vm = viewModel()

        vm.onRestoreUriSelected(uri)
        advanceUntilIdle()
        // Swallow the expected error so it doesn't fail the test through errorEvents
        val errorJob = launch { vm.errorEvents.take(1).toList() }

        vm.onConfirmRestore()
        advanceUntilIdle()
        errorJob.join()

        val state = vm.state.filterNotNull().first()
        state.pendingRestore.shouldNotBeNull()
        state.lastResult shouldBe null
        state.isWorking shouldBe false
    }

    @Test
    fun `onCancelRestore clears pendingRestore`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        coEvery { backupRestoreManager.parseBackup(uri) } returns BackupRestoreManager.ParseResult(
            backup = sampleBackup,
            versionMismatch = false,
            enumWarnings = emptyList(),
        )
        every { backupRestoreManager.resolveFileName(uri) } returns "r.zip"
        val vm = viewModel()

        vm.onRestoreUriSelected(uri)
        advanceUntilIdle()
        vm.onCancelRestore()
        advanceUntilIdle()

        vm.state.filterNotNull().first().pendingRestore shouldBe null
    }

    @Test
    fun `onToggleSkipExisting updates state`() = runTest {
        val vm = viewModel()

        vm.onToggleSkipExisting(true)
        advanceUntilIdle()

        vm.state.filterNotNull().first().skipExisting shouldBe true
    }

    @Test
    fun `createBackup exception propagates via errorEvents and clears isWorking`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        coEvery { backupRestoreManager.createBackup(uri) } throws IOException("disk full")
        val vm = viewModel()

        val errors = mutableListOf<Throwable>()
        val job = launch { vm.errorEvents.take(1).toList(errors) }

        vm.onBackupUriSelected(uri)
        advanceUntilIdle()
        job.join()

        errors.single().message shouldBe "disk full"
        val state = vm.state.filterNotNull().first()
        state.isWorking shouldBe false
        state.lastResult shouldBe null
    }
}
