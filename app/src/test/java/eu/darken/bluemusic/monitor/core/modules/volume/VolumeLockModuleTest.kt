package eu.darken.bluemusic.monitor.core.modules.volume

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeModeTool
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class VolumeLockModuleTest : BaseTest() {

    private val address = "AA:BB:CC:DD:EE:FF"

    private lateinit var volumeTool: VolumeTool
    private lateinit var volumeModeTool: VolumeModeTool
    private lateinit var deviceRepo: DeviceRepo
    private lateinit var ownerRegistry: AudioStreamOwnerRegistry
    private lateinit var sourceDevice: SourceDevice
    private lateinit var devicesFlow: MutableStateFlow<List<ManagedDevice>>

    @BeforeEach
    fun setup() {
        volumeTool = mockk(relaxed = true)
        volumeModeTool = mockk(relaxed = true)
        deviceRepo = mockk(relaxed = true)
        ownerRegistry = AudioStreamOwnerRegistry()
        devicesFlow = MutableStateFlow(emptyList())
        every { deviceRepo.devices } returns devicesFlow

        sourceDevice = mockk {
            every { this@mockk.address } returns this@VolumeLockModuleTest.address
            every { label } returns "Test Device"
            every { deviceType } returns SourceDevice.Type.HEADPHONES
            every { getStreamId(AudioStream.Type.MUSIC) } returns AudioStream.Id.STREAM_MUSIC
            every { getStreamId(AudioStream.Type.CALL) } returns AudioStream.Id.STREAM_VOICE_CALL
            every { getStreamId(AudioStream.Type.RINGTONE) } returns AudioStream.Id.STREAM_RINGTONE
            every { getStreamId(AudioStream.Type.NOTIFICATION) } returns AudioStream.Id.STREAM_NOTIFICATION
            every { getStreamId(AudioStream.Type.ALARM) } returns AudioStream.Id.STREAM_ALARM
        }
    }

    private fun createModule() = VolumeLockModule(
        volumeTool = volumeTool,
        volumeModeTool = volumeModeTool,
        deviceRepo = deviceRepo,
        ownerRegistry = ownerRegistry,
    )

    private fun config(
        addr: String = address,
        musicVolume: Float? = null,
        callVolume: Float? = null,
        ringVolume: Float? = null,
        notificationVolume: Float? = null,
        alarmVolume: Float? = null,
        volumeLock: Boolean = false,
        isEnabled: Boolean = true,
    ): DeviceConfigEntity = DeviceConfigEntity(
        address = addr,
        musicVolume = musicVolume,
        callVolume = callVolume,
        ringVolume = ringVolume,
        notificationVolume = notificationVolume,
        alarmVolume = alarmVolume,
        volumeLock = volumeLock,
        isEnabled = isEnabled,
    )

    private fun managedDevice(
        config: DeviceConfigEntity,
        device: SourceDevice = sourceDevice,
    ) = ManagedDevice(
        isConnected = true,
        device = device,
        config = config,
    )

    private suspend fun seedOwner(device: ManagedDevice) {
        devicesFlow.value = listOf(device)
        ownerRegistry.onDeviceConnected(
            address = device.address,
            label = device.label,
            deviceType = device.type,
            receivedAtElapsedMs = 1000L,
            sequence = 0L,
        )
    }

    @Test
    fun `lock with Normal volume uses VolumeModeTool`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f, volumeLock = true)
        seedOwner(managedDevice(cfg))

        every { volumeTool.wasUs(any(), any()) } returns false
        coEvery { volumeModeTool.apply(any(), any(), any(), any()) } returns true

        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 11, self = false))

        coVerify(exactly = 1) {
            volumeModeTool.apply(
                streamId = AudioStream.Id.STREAM_MUSIC,
                streamType = AudioStream.Type.MUSIC,
                volumeMode = VolumeMode.Normal(0.5f),
                visible = false,
            )
        }
    }

    @Test
    fun `lock with Silent sentinel uses VolumeModeTool not raw changeVolume`() = runTest {
        val module = createModule()
        val cfg = config(ringVolume = VolumeMode.LEGACY_SILENT_VALUE, volumeLock = true)
        seedOwner(managedDevice(cfg))

        every { volumeTool.wasUs(any(), any()) } returns false
        coEvery { volumeModeTool.apply(any(), any(), any(), any()) } returns true

        module.handle(VolumeEvent(AudioStream.Id.STREAM_RINGTONE, oldVolume = 5, newVolume = 3, self = false))

        coVerify(exactly = 1) {
            volumeModeTool.apply(
                streamId = AudioStream.Id.STREAM_RINGTONE,
                streamType = AudioStream.Type.RINGTONE,
                volumeMode = VolumeMode.Silent,
                visible = false,
            )
        }
    }

    @Test
    fun `lock with Vibrate sentinel uses VolumeModeTool`() = runTest {
        val module = createModule()
        val cfg = config(ringVolume = VolumeMode.LEGACY_VIBRATE_VALUE, volumeLock = true)
        seedOwner(managedDevice(cfg))

        every { volumeTool.wasUs(any(), any()) } returns false
        coEvery { volumeModeTool.apply(any(), any(), any(), any()) } returns true

        module.handle(VolumeEvent(AudioStream.Id.STREAM_RINGTONE, oldVolume = 5, newVolume = 3, self = false))

        coVerify(exactly = 1) {
            volumeModeTool.apply(
                streamId = AudioStream.Id.STREAM_RINGTONE,
                streamType = AudioStream.Type.RINGTONE,
                volumeMode = VolumeMode.Vibrate,
                visible = false,
            )
        }
    }

    @Test
    fun `lock with null volume skips device`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = null, volumeLock = true)
        seedOwner(managedDevice(cfg))

        every { volumeTool.wasUs(any(), any()) } returns false

        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 11, self = false))

        coVerify(exactly = 0) { volumeModeTool.apply(any(), any(), any(), any()) }
    }

    @Test
    fun `lock filters only active and volumeLock devices`() = runTest {
        val module = createModule()
        val locked = config(musicVolume = 0.5f, volumeLock = true)
        val unlocked = config(musicVolume = 0.7f, volumeLock = false)
        val dev1 = managedDevice(locked)
        val dev2 = managedDevice(unlocked)
        devicesFlow.value = listOf(dev1, dev2)
        ownerRegistry.onDeviceConnected(address, "Test Device", SourceDevice.Type.HEADPHONES, 1000L, 0L)

        every { volumeTool.wasUs(any(), any()) } returns false
        coEvery { volumeModeTool.apply(any(), any(), any(), any()) } returns true

        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 11, self = false))

        coVerify(exactly = 1) { volumeModeTool.apply(any(), any(), any(), any()) }
    }

    @Test
    fun `self-triggered change is ignored`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f, volumeLock = true)
        seedOwner(managedDevice(cfg))

        every { volumeTool.wasUs(AudioStream.Id.STREAM_MUSIC, 11) } returns true

        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 11, self = false))

        coVerify(exactly = 0) { volumeModeTool.apply(any(), any(), any(), any()) }
    }

    @Test
    fun `non-owner device with volumeLock is not enforced`() = runTest {
        val module = createModule()
        val address2 = "11:22:33:44:55:66"
        val sourceDevice2: SourceDevice = mockk {
            every { this@mockk.address } returns address2
            every { label } returns "Other Device"
            every { deviceType } returns SourceDevice.Type.HEADPHONES
            every { getStreamId(AudioStream.Type.MUSIC) } returns AudioStream.Id.STREAM_MUSIC
            every { getStreamId(AudioStream.Type.CALL) } returns AudioStream.Id.STREAM_VOICE_CALL
            every { getStreamId(AudioStream.Type.RINGTONE) } returns AudioStream.Id.STREAM_RINGTONE
            every { getStreamId(AudioStream.Type.NOTIFICATION) } returns AudioStream.Id.STREAM_NOTIFICATION
            every { getStreamId(AudioStream.Type.ALARM) } returns AudioStream.Id.STREAM_ALARM
        }

        val ownerCfg = config(musicVolume = 0.5f, volumeLock = true)
        val nonOwnerCfg = config(addr = address2, musicVolume = 0.8f, volumeLock = true)
        val ownerDev = managedDevice(ownerCfg)
        val nonOwnerDev = managedDevice(nonOwnerCfg, device = sourceDevice2)

        devicesFlow.value = listOf(ownerDev, nonOwnerDev)
        ownerRegistry.onDeviceConnected(address, "Test Device", SourceDevice.Type.HEADPHONES, 1000L, 0L)
        ownerRegistry.onDeviceConnected(address2, "Other Device", SourceDevice.Type.HEADPHONES, 500L, 1L)

        every { volumeTool.wasUs(any(), any()) } returns false
        coEvery { volumeModeTool.apply(any(), any(), any(), any()) } returns true

        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 11, self = false))

        coVerify(exactly = 1) {
            volumeModeTool.apply(
                streamId = AudioStream.Id.STREAM_MUSIC,
                streamType = AudioStream.Type.MUSIC,
                volumeMode = VolumeMode.Normal(0.5f),
                visible = false,
            )
        }
    }
}
