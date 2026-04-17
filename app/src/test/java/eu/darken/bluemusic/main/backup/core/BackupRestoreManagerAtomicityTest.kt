package eu.darken.bluemusic.main.backup.core

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.database.DeviceConfigDao
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.devices.core.database.DeviceDatabase
import eu.darken.bluemusic.devices.core.database.DevicesRoomDb
import eu.darken.bluemusic.main.core.GeneralSettings
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.coroutine.TestDispatcherProvider
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BackupRestoreManagerAtomicityTest {

    private lateinit var roomDb: DevicesRoomDb
    private lateinit var deviceDatabase: DeviceDatabase
    private lateinit var manager: BackupRestoreManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        roomDb = Room.inMemoryDatabaseBuilder(context, DevicesRoomDb::class.java)
            .allowMainThreadQueries()
            .build()

        deviceDatabase = DeviceDatabase(context).apply {
            setDatabaseForTest(roomDb)
        }

        val devicesSettings = mockk<DevicesSettings>(relaxed = true)
        coEvery { devicesSettings.toBackup() } returns DevicesSettingsBackup()
        coEvery { devicesSettings.applyBackup(any()) } just Runs

        val generalSettings = mockk<GeneralSettings>(relaxed = true)
        coEvery { generalSettings.toBackup() } returns GeneralSettingsBackup()
        coEvery { generalSettings.applyBackup(any()) } just Runs
        every { generalSettings.detectUnknownEnums(any()) } returns emptyList()

        val contextMock = mockk<Context>(relaxed = true)

        manager = BackupRestoreManager(
            context = contextMock,
            deviceDatabase = deviceDatabase,
            devicesSettings = devicesSettings,
            generalSettings = generalSettings,
            json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false },
            dispatcherProvider = TestDispatcherProvider(),
        )
    }

    @After
    fun tearDown() {
        roomDb.close()
    }

    @Test
    fun `device-write exception rolls back transaction`() = runBlocking {
        val existing1 = DeviceConfigEntity(address = "AA:AA:AA:AA:AA:AA", customName = "Original A")
        val existing2 = DeviceConfigEntity(address = "BB:BB:BB:BB:BB:BB", customName = "Original B")
        roomDb.devices().updateDevice(existing1)
        roomDb.devices().updateDevice(existing2)

        deviceDatabase.setDaoForTest(ThrowingDao(roomDb.devices(), throwAfterWrites = 1))

        val incomingBackup = AppBackup(
            formatVersion = 1,
            appVersion = "1.0.0",
            createdAt = "2026-01-01T00:00:00Z",
            deviceConfigs = listOf(
                DeviceConfigBackup(address = "CC:CC:CC:CC:CC:CC", customName = "New C"),
                DeviceConfigBackup(address = "DD:DD:DD:DD:DD:DD", customName = "New D"),
            ),
        )

        shouldThrow<IOException> {
            manager.applyRestore(incomingBackup, skipExisting = false)
        }

        deviceDatabase.setDaoForTest(null)
        val actual = roomDb.devices().getAllDevicesOnce()
        actual.map { it.address } shouldContainExactlyInAnyOrder listOf(
            "AA:AA:AA:AA:AA:AA",
            "BB:BB:BB:BB:BB:BB",
        )
        actual.first { it.address == "AA:AA:AA:AA:AA:AA" }.customName shouldBe "Original A"
    }

    @Test
    fun `successful restore writes all rows`() = runBlocking {
        val incomingBackup = AppBackup(
            formatVersion = 1,
            appVersion = "1.0.0",
            createdAt = "2026-01-01T00:00:00Z",
            deviceConfigs = listOf(
                DeviceConfigBackup(address = "AA:AA:AA:AA:AA:AA", customName = "Alpha"),
                DeviceConfigBackup(address = "BB:BB:BB:BB:BB:BB", customName = "Beta"),
            ),
        )

        manager.applyRestore(incomingBackup, skipExisting = false)

        val actual = roomDb.devices().getAllDevicesOnce()
        actual.map { it.address } shouldContainExactlyInAnyOrder listOf(
            "AA:AA:AA:AA:AA:AA",
            "BB:BB:BB:BB:BB:BB",
        )
    }

    private class ThrowingDao(
        private val delegate: DeviceConfigDao,
        private val throwAfterWrites: Int,
    ) : DeviceConfigDao by delegate {
        private var writeCount = 0

        override suspend fun updateDevice(device: DeviceConfigEntity) {
            writeCount++
            if (writeCount > throwAfterWrites) throw IOException("simulated mid-loop failure")
            delegate.updateDevice(device)
        }
    }
}
