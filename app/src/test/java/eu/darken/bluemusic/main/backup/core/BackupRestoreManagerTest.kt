package eu.darken.bluemusic.main.backup.core

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.database.DeviceConfigDao
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.devices.core.database.DeviceDatabase
import eu.darken.bluemusic.main.core.GeneralSettings
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupRestoreManagerTest : BaseTest() {

    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var deviceDatabase: DeviceDatabase
    private lateinit var dao: DeviceConfigDao
    private lateinit var devicesSettings: DevicesSettings
    private lateinit var generalSettings: GeneralSettings
    private lateinit var json: Json
    private lateinit var manager: BackupRestoreManager

    private val sampleEntity = DeviceConfigEntity(
        address = "AA:BB:CC:DD:EE:FF",
        customName = "Test Device",
        volumeLock = true,
    )

    private val sampleDeviceBackup = DeviceConfigBackup(
        address = "AA:BB:CC:DD:EE:FF",
        customName = "Backup Device",
    )

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        every { context.contentResolver } returns contentResolver

        dao = mockk(relaxed = true)
        deviceDatabase = mockk(relaxed = true)
        every { deviceDatabase.devices } returns dao
        coEvery { deviceDatabase.withTransaction<Any?>(any()) } coAnswers {
            val block = firstArg<suspend () -> Any?>()
            block()
        }
        coEvery { dao.getAllDevicesOnce() } returns emptyList()

        devicesSettings = mockk(relaxed = true)
        generalSettings = mockk(relaxed = true)
        coEvery { devicesSettings.toBackup() } returns DevicesSettingsBackup()
        coEvery { generalSettings.toBackup() } returns GeneralSettingsBackup()
        coEvery { devicesSettings.applyBackup(any()) } just Runs
        coEvery { generalSettings.applyBackup(any()) } just Runs
        every { generalSettings.detectUnknownEnums(any()) } returns emptyList()

        json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

        manager = BackupRestoreManager(
            context = context,
            deviceDatabase = deviceDatabase,
            devicesSettings = devicesSettings,
            generalSettings = generalSettings,
            json = json,
            dispatcherProvider = TestDispatcherProvider(),
        )
    }

    private fun tempUri(name: String): Pair<Uri, File> {
        val file = File(tempDir, name)
        val uri = mockk<Uri>(relaxed = true)
        every { uri.lastPathSegment } returns name
        every { contentResolver.openOutputStream(uri) } answers { FileOutputStream(file) }
        every { contentResolver.openInputStream(uri) } answers {
            if (file.exists()) FileInputStream(file) else null
        }
        return uri to file
    }

    @Test
    fun `createBackup writes zip containing backup json`() = runTest {
        val (uri, file) = tempUri("out.zip")
        coEvery { dao.getAllDevicesOnce() } returns listOf(sampleEntity)

        val result = manager.createBackup(uri)

        result.deviceConfigCount shouldBe 1
        file.exists() shouldBe true
        file.length() shouldBe file.length().also { it > 0 }
    }

    @Test
    fun `createBackup round-trips through parseBackup`() = runTest {
        val (uri, _) = tempUri("round.zip")
        coEvery { dao.getAllDevicesOnce() } returns listOf(sampleEntity)

        manager.createBackup(uri)
        val parsed = manager.parseBackup(uri)

        parsed.backup.deviceConfigs shouldContain sampleEntity.toBackup()
    }

    @Test
    fun `createBackup with empty device list produces valid zip`() = runTest {
        val (uri, _) = tempUri("empty.zip")
        coEvery { dao.getAllDevicesOnce() } returns emptyList()

        val result = manager.createBackup(uri)
        val parsed = manager.parseBackup(uri)

        result.deviceConfigCount shouldBe 0
        parsed.backup.deviceConfigs shouldBe emptyList()
    }

    @Test
    fun `createBackup throws BackupIoError when openOutputStream returns null`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        every { contentResolver.openOutputStream(uri) } returns null

        shouldThrow<BackupError.BackupIoError> {
            manager.createBackup(uri)
        }
    }

    @Test
    fun `parseBackup throws BackupIoError when openInputStream returns null`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        every { contentResolver.openInputStream(uri) } returns null

        shouldThrow<BackupError.BackupIoError> {
            manager.parseBackup(uri)
        }
    }

    @Test
    fun `parseBackup throws MissingBackupJson on zip without backup entry`() = runTest {
        val file = File(tempDir, "bogus.zip")
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            zip.putNextEntry(ZipEntry("other.txt"))
            zip.write("hello".toByteArray())
            zip.closeEntry()
        }
        val uri = mockk<Uri>(relaxed = true)
        every { contentResolver.openInputStream(uri) } answers { FileInputStream(file) }

        shouldThrow<BackupError.MissingBackupJson> {
            manager.parseBackup(uri)
        }
    }

    @Test
    fun `parseBackup on non-zip garbage surfaces as BackupError`() = runTest {
        val file = File(tempDir, "garbage.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        val uri = mockk<Uri>(relaxed = true)
        every { contentResolver.openInputStream(uri) } answers { FileInputStream(file) }

        shouldThrow<BackupError> {
            manager.parseBackup(uri)
        }
    }

    @Test
    fun `parseBackup throws MalformedBackup on bad json inside valid zip`() = runTest {
        val file = File(tempDir, "bad.zip")
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write("{ this is not json".toByteArray())
            zip.closeEntry()
        }
        val uri = mockk<Uri>(relaxed = true)
        every { contentResolver.openInputStream(uri) } answers { FileInputStream(file) }

        shouldThrow<BackupError.MalformedBackup> {
            manager.parseBackup(uri)
        }
    }

    @Test
    fun `parseBackup throws UnsupportedFormatVersion on mismatched format`() = runTest {
        val file = File(tempDir, "fmt.zip")
        val backupJson = """
            {"formatVersion": 99, "appVersion": "9.9.9", "createdAt": "2026-01-01T00:00:00Z"}
        """.trimIndent()
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(backupJson.toByteArray())
            zip.closeEntry()
        }
        val uri = mockk<Uri>(relaxed = true)
        every { contentResolver.openInputStream(uri) } answers { FileInputStream(file) }

        shouldThrow<BackupError.UnsupportedFormatVersion> {
            manager.parseBackup(uri)
        }
    }

    @Test
    fun `parseBackup flags duplicate addresses in warnings`() = runTest {
        val (uri, file) = tempUri("dup.zip")
        val backup = AppBackup(
            formatVersion = 1,
            appVersion = "1.0.0",
            createdAt = "2026-01-01T00:00:00Z",
            deviceConfigs = listOf(
                DeviceConfigBackup(address = "AA:AA:AA:AA:AA:AA"),
                DeviceConfigBackup(address = "AA:AA:AA:AA:AA:AA", customName = "dup"),
                DeviceConfigBackup(address = "BB:BB:BB:BB:BB:BB"),
            ),
        )
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(json.encodeToString(AppBackup.serializer(), backup).toByteArray())
            zip.closeEntry()
        }

        val parsed = manager.parseBackup(uri)

        parsed.enumWarnings.any { it.contains("AA:AA:AA:AA:AA:AA") } shouldBe true
    }

    @Test
    fun `applyRestore calls settings applyBackup after device writes`() = runTest {
        val backup = buildBackup(listOf(sampleDeviceBackup))

        manager.applyRestore(backup, skipExisting = false)

        coVerifyOrder {
            dao.updateDevice(any())
            devicesSettings.applyBackup(any())
            generalSettings.applyBackup(any())
        }
    }

    @Test
    fun `applyRestore skips existing addresses when skipExisting true`() = runTest {
        coEvery { dao.getAllDevicesOnce() } returns listOf(sampleEntity)
        val backup = buildBackup(
            listOf(
                sampleDeviceBackup,
                DeviceConfigBackup(address = "CC:CC:CC:CC:CC:CC"),
            )
        )

        val result = manager.applyRestore(backup, skipExisting = true)

        result.skippedCount shouldBe 1
        result.deviceCount shouldBe 1
        val written = slot<DeviceConfigEntity>()
        coVerify(exactly = 1) { dao.updateDevice(capture(written)) }
        written.captured.address shouldBe "CC:CC:CC:CC:CC:CC"
    }

    @Test
    fun `applyRestore deduplicates by address`() = runTest {
        val backup = buildBackup(
            listOf(
                DeviceConfigBackup(address = "AA:AA:AA:AA:AA:AA", customName = "first"),
                DeviceConfigBackup(address = "AA:AA:AA:AA:AA:AA", customName = "second"),
                DeviceConfigBackup(address = "BB:BB:BB:BB:BB:BB"),
            )
        )

        val result = manager.applyRestore(backup, skipExisting = false)

        result.deviceCount shouldBe 2
        val writes = mutableListOf<DeviceConfigEntity>()
        coVerify(exactly = 2) { dao.updateDevice(capture(writes)) }
        writes.map { it.address } shouldContainExactlyInAnyOrder listOf(
            "AA:AA:AA:AA:AA:AA",
            "BB:BB:BB:BB:BB:BB",
        )
        writes.first { it.address == "AA:AA:AA:AA:AA:AA" }.customName shouldBe "first"
    }

    @Test
    fun `applyRestore propagates device-loop exception and skips settings`() = runTest {
        coEvery { dao.updateDevice(any()) } throws IOException("write failed")
        val backup = buildBackup(listOf(sampleDeviceBackup))

        shouldThrow<IOException> {
            manager.applyRestore(backup, skipExisting = false)
        }
        coVerify(exactly = 0) { devicesSettings.applyBackup(any()) }
        coVerify(exactly = 0) { generalSettings.applyBackup(any()) }
    }

    private fun buildBackup(devices: List<DeviceConfigBackup>): AppBackup = AppBackup(
        formatVersion = 1,
        appVersion = "1.0.0",
        createdAt = "2026-01-01T00:00:00Z",
        deviceConfigs = devices,
    )
}
