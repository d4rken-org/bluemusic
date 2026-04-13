package eu.darken.bluemusic.monitor.core.modules.volume

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.RingerMode
import eu.darken.bluemusic.monitor.core.audio.RingerTool
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class VolumeUpdateModuleTest : BaseTest() {

    private val address = "AA:BB:CC:DD:EE:FF"

    private lateinit var volumeTool: VolumeTool
    private lateinit var ringerTool: RingerTool
    private lateinit var deviceRepo: DeviceRepo
    private lateinit var observationGate: VolumeObservationGate
    private lateinit var ownerRegistry: eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
    private lateinit var sourceDevice: SourceDevice
    private lateinit var devicesFlow: MutableStateFlow<List<ManagedDevice>>

    @BeforeEach
    fun setup() {
        volumeTool = mockk(relaxed = true)
        ringerTool = mockk(relaxed = true)
        deviceRepo = mockk(relaxed = true)
        observationGate = VolumeObservationGate()
        ownerRegistry = eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry()
        devicesFlow = MutableStateFlow(emptyList())
        every { deviceRepo.devices } returns devicesFlow
        coEvery { deviceRepo.updateDevice(any(), any()) } just Runs

        sourceDevice = mockk {
            every { this@mockk.address } returns this@VolumeUpdateModuleTest.address
            every { label } returns "Test Device"
            every { deviceType } returns SourceDevice.Type.HEADPHONES
            every { getStreamId(AudioStream.Type.MUSIC) } returns AudioStream.Id.STREAM_MUSIC
            every { getStreamId(AudioStream.Type.CALL) } returns AudioStream.Id.STREAM_VOICE_CALL
            every { getStreamId(AudioStream.Type.RINGTONE) } returns AudioStream.Id.STREAM_RINGTONE
            every { getStreamId(AudioStream.Type.NOTIFICATION) } returns AudioStream.Id.STREAM_NOTIFICATION
            every { getStreamId(AudioStream.Type.ALARM) } returns AudioStream.Id.STREAM_ALARM
        }
    }

    private fun createModule() = VolumeUpdateModule(
        volumeTool = volumeTool,
        ringerTool = ringerTool,
        deviceRepo = deviceRepo,
        observationGate = observationGate,
        ownerRegistry = ownerRegistry,
    )

    private fun config(
        musicVolume: Float? = null,
        callVolume: Float? = null,
        ringVolume: Float? = null,
        notificationVolume: Float? = null,
        alarmVolume: Float? = null,
        volumeObserving: Boolean = true,
        volumeLock: Boolean = false,
        lastConnected: Long = 0L,
    ): DeviceConfigEntity = DeviceConfigEntity(
        address = address,
        musicVolume = musicVolume,
        callVolume = callVolume,
        ringVolume = ringVolume,
        notificationVolume = notificationVolume,
        alarmVolume = alarmVolume,
        volumeObserving = volumeObserving,
        volumeLock = volumeLock,
        lastConnected = lastConnected,
    )

    private fun managedDevice(config: DeviceConfigEntity) = ManagedDevice(
        isConnected = true,
        device = sourceDevice,
        config = config,
    )

    private suspend fun seedActive(device: ManagedDevice) {
        devicesFlow.value = listOf(device)
        ownerRegistry.onDeviceConnected(
            address = device.address,
            label = device.label,
            deviceType = device.type,
            receivedAtElapsedMs = 1000L,
            sequence = 0L,
        )
    }

    private suspend fun runTransform(
        module: VolumeUpdateModule,
        event: VolumeEvent,
        seedConfig: DeviceConfigEntity,
    ): DeviceConfigEntity {
        val slot = slot<(DeviceConfigEntity) -> DeviceConfigEntity>()
        coEvery { deviceRepo.updateDevice(address, capture(slot)) } just Runs
        module.handle(event)
        return slot.captured(seedConfig)
    }

    // ------------------------------------------------------------------------
    // wasUs guard — self-triggered events are ignored
    // ------------------------------------------------------------------------
    @Test
    fun `self-triggered events are ignored`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        seedActive(managedDevice(cfg))

        every { volumeTool.wasUs(AudioStream.Id.STREAM_MUSIC, 11) } returns true

        module.handle(
            VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 11, self = false)
        )

        coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
    }

    // ------------------------------------------------------------------------
    // observation gate — volume changes for suppressed streams are not persisted
    // ------------------------------------------------------------------------
    @Test
    fun `volume changes for suppressed streams are not persisted`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        seedActive(managedDevice(cfg))

        every { volumeTool.wasUs(any(), any()) } returns false
        observationGate.suppress(AudioStream.Id.STREAM_MUSIC)

        module.handle(
            VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 11, self = false)
        )

        coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
    }

    @Test
    fun `volume changes for unsuppressed streams are persisted`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        seedActive(managedDevice(cfg))

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        every { volumeTool.getVolumePercentage(AudioStream.Id.STREAM_MUSIC) } returns 0.44f
        every { volumeTool.wasUs(any(), any()) } returns false

        val token = observationGate.suppress(AudioStream.Id.STREAM_MUSIC)
        observationGate.unsuppress(token)

        module.handle(
            VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 11, self = false)
        )

        coVerify(exactly = 1) { deviceRepo.updateDevice(any(), any()) }
    }

    @Test
    fun `mirrored stream suppression blocks BLUETOOTH_HANDSFREE when VOICE_CALL is suppressed`() = runTest {
        val module = createModule()
        val cfg = config(callVolume = 1.0f)
        seedActive(managedDevice(cfg))

        every { volumeTool.wasUs(any(), any()) } returns false
        observationGate.suppress(AudioStream.Id.STREAM_VOICE_CALL)

        module.handle(
            VolumeEvent(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, oldVolume = 15, newVolume = 11, self = false)
        )

        coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
    }

    // ------------------------------------------------------------------------
    // Normal ringer + MUSIC → writes percent
    // ------------------------------------------------------------------------
    @Test
    fun `normal ringer music change writes percent`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        seedActive(managedDevice(cfg))

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        every { volumeTool.getVolumePercentage(AudioStream.Id.STREAM_MUSIC) } returns 0.32f
        every { volumeTool.wasUs(any(), any()) } returns false

        val result = runTransform(
            module,
            VolumeEvent(AudioStream.Id.STREAM_MUSIC, 11, 8, self = false),
            cfg,
        )

        result.musicVolume shouldBe 0.32f
    }

    // ------------------------------------------------------------------------
    // RINGTONE in VIBRATE → writes Vibrate sentinel (not Normal(0))
    //
    // Regression: without the ringer-aware mapping, the STREAM_RING→0
    // observation that Android fires on every vibrate flip would silently
    // overwrite a stored Vibrate sentinel (or Normal value) with 0.
    // ------------------------------------------------------------------------
    @Test
    fun `vibrate ringer ring change writes vibrate sentinel`() = runTest {
        val module = createModule()
        val cfg = config(ringVolume = 0.48f)
        seedActive(managedDevice(cfg))

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.VIBRATE
        every { volumeTool.getVolumePercentage(AudioStream.Id.STREAM_RINGTONE) } returns 0f
        every { volumeTool.wasUs(any(), any()) } returns false

        val result = runTransform(
            module,
            VolumeEvent(AudioStream.Id.STREAM_RINGTONE, 5, 0, self = false),
            cfg,
        )

        result.ringVolume shouldBe VolumeMode.LEGACY_VIBRATE_VALUE
    }

    // ------------------------------------------------------------------------
    // RINGTONE in SILENT → writes Silent sentinel
    // ------------------------------------------------------------------------
    @Test
    fun `silent ringer ring change writes silent sentinel`() = runTest {
        val module = createModule()
        val cfg = config(ringVolume = 0.48f)
        seedActive(managedDevice(cfg))

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.SILENT
        every { volumeTool.getVolumePercentage(AudioStream.Id.STREAM_RINGTONE) } returns 0f
        every { volumeTool.wasUs(any(), any()) } returns false

        val result = runTransform(
            module,
            VolumeEvent(AudioStream.Id.STREAM_RINGTONE, 5, 0, self = false),
            cfg,
        )

        result.ringVolume shouldBe VolumeMode.LEGACY_SILENT_VALUE
    }

    // ------------------------------------------------------------------------
    // NOTIFICATION in VIBRATE with hardware 0 → skipped (preserves stored)
    //
    // Matches the disconnect-module heuristic: a 0 reading under non-Normal
    // ringer is ambiguous, preserve the stored value rather than zero it out.
    // ------------------------------------------------------------------------
    @Test
    fun `vibrate ringer notification zero hardware preserves stored`() = runTest {
        val module = createModule()
        val cfg = config(notificationVolume = 0.19f)
        seedActive(managedDevice(cfg))

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.VIBRATE
        every { volumeTool.getVolumePercentage(AudioStream.Id.STREAM_NOTIFICATION) } returns 0f
        every { volumeTool.wasUs(any(), any()) } returns false

        module.handle(
            VolumeEvent(AudioStream.Id.STREAM_NOTIFICATION, 1, 0, self = false)
        )

        coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
    }

    // ------------------------------------------------------------------------
    // NOTIFICATION in VIBRATE with hardware > 0 → captured (non-coupling device)
    // ------------------------------------------------------------------------
    @Test
    fun `vibrate ringer notification nonzero hardware captures change`() = runTest {
        val module = createModule()
        val cfg = config(notificationVolume = 0.19f)
        seedActive(managedDevice(cfg))

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.VIBRATE
        every { volumeTool.getVolumePercentage(AudioStream.Id.STREAM_NOTIFICATION) } returns (5f / 7f)
        every { volumeTool.wasUs(any(), any()) } returns false

        val result = runTransform(
            module,
            VolumeEvent(AudioStream.Id.STREAM_NOTIFICATION, 1, 5, self = false),
            cfg,
        )

        result.notificationVolume shouldBe (5f / 7f)
    }

    // ------------------------------------------------------------------------
    // volumeObserving=false → no write
    // ------------------------------------------------------------------------
    @Test
    fun `volumeObserving disabled - no write`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f, volumeObserving = false)
        seedActive(managedDevice(cfg))

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        every { volumeTool.getVolumePercentage(AudioStream.Id.STREAM_MUSIC) } returns 0.7f
        every { volumeTool.wasUs(any(), any()) } returns false

        module.handle(
            VolumeEvent(AudioStream.Id.STREAM_MUSIC, 11, 17, self = false)
        )

        coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
    }

    // ------------------------------------------------------------------------
    // volumeLock=true → no write
    // ------------------------------------------------------------------------
    @Test
    fun `volumeLock enabled - no write`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f, volumeLock = true)
        seedActive(managedDevice(cfg))

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        every { volumeTool.getVolumePercentage(AudioStream.Id.STREAM_MUSIC) } returns 0.7f
        every { volumeTool.wasUs(any(), any()) } returns false

        module.handle(
            VolumeEvent(AudioStream.Id.STREAM_MUSIC, 11, 17, self = false)
        )

        coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
    }

    // ------------------------------------------------------------------------
    // Unconfigured stream (no stored value) → no write
    // ------------------------------------------------------------------------
    @Test
    fun `unconfigured stream - no write`() = runTest {
        val module = createModule()
        // musicVolume explicitly null — this device does not track music volume
        val cfg = config(musicVolume = null, callVolume = 0.3f)
        seedActive(managedDevice(cfg))

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        every { volumeTool.getVolumePercentage(AudioStream.Id.STREAM_MUSIC) } returns 0.7f
        every { volumeTool.wasUs(any(), any()) } returns false

        module.handle(
            VolumeEvent(AudioStream.Id.STREAM_MUSIC, 11, 17, self = false)
        )

        coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
    }

    // ------------------------------------------------------------------------
    // Owner group config filtering: volumeObserving=false within group
    // ------------------------------------------------------------------------
    @Test
    fun `owner group member with volumeObserving false is skipped`() = runTest {
        val module = createModule()
        val stableTime = System.currentTimeMillis() - 60_000L

        val dev1 = makeSourceDevice("AA:BB:CC:DD:EE:01", "Buds3 Pro")
        val dev2 = makeSourceDevice("AA:BB:CC:DD:EE:02", "Buds3 Pro")
        val cfg1 = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:01", musicVolume = 0.5f, volumeObserving = true, lastConnected = stableTime)
        val cfg2 = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:02", musicVolume = 0.5f, volumeObserving = false, lastConnected = stableTime)
        devicesFlow.value = listOf(
            ManagedDevice(isConnected = true, device = dev1, config = cfg1),
            ManagedDevice(isConnected = true, device = dev2, config = cfg2),
        )

        ownerRegistry.onDeviceConnected("AA:BB:CC:DD:EE:01", "Buds3 Pro", SourceDevice.Type.HEADPHONES, 1000L, 0L)
        ownerRegistry.onDeviceConnected("AA:BB:CC:DD:EE:02", "Buds3 Pro", SourceDevice.Type.HEADPHONES, 1002L, 1L)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        every { volumeTool.getVolumePercentage(AudioStream.Id.STREAM_MUSIC) } returns 0.7f
        every { volumeTool.wasUs(any(), any()) } returns false

        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, 5, 11, self = false))

        coVerify(exactly = 1) { deviceRepo.updateDevice("AA:BB:CC:DD:EE:01", any()) }
        coVerify(exactly = 0) { deviceRepo.updateDevice("AA:BB:CC:DD:EE:02", any()) }
    }

    // ------------------------------------------------------------------------
    // Owner group config filtering: volumeLock=true within group
    // ------------------------------------------------------------------------
    @Test
    fun `owner group member with volumeLock true is skipped`() = runTest {
        val module = createModule()
        val stableTime = System.currentTimeMillis() - 60_000L

        val dev1 = makeSourceDevice("AA:BB:CC:DD:EE:01", "Buds3 Pro")
        val dev2 = makeSourceDevice("AA:BB:CC:DD:EE:02", "Buds3 Pro")
        val cfg1 = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:01", musicVolume = 0.5f, volumeObserving = true, lastConnected = stableTime)
        val cfg2 = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:02", musicVolume = 0.5f, volumeObserving = true, volumeLock = true, lastConnected = stableTime)
        devicesFlow.value = listOf(
            ManagedDevice(isConnected = true, device = dev1, config = cfg1),
            ManagedDevice(isConnected = true, device = dev2, config = cfg2),
        )

        ownerRegistry.onDeviceConnected("AA:BB:CC:DD:EE:01", "Buds3 Pro", SourceDevice.Type.HEADPHONES, 1000L, 0L)
        ownerRegistry.onDeviceConnected("AA:BB:CC:DD:EE:02", "Buds3 Pro", SourceDevice.Type.HEADPHONES, 1002L, 1L)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        every { volumeTool.getVolumePercentage(AudioStream.Id.STREAM_MUSIC) } returns 0.7f
        every { volumeTool.wasUs(any(), any()) } returns false

        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, 5, 11, self = false))

        coVerify(exactly = 1) { deviceRepo.updateDevice("AA:BB:CC:DD:EE:01", any()) }
        coVerify(exactly = 0) { deviceRepo.updateDevice("AA:BB:CC:DD:EE:02", any()) }
    }

    // ------------------------------------------------------------------------
    // Multi-device characterization: documents current fan-out behavior
    // ------------------------------------------------------------------------

    private fun makeSourceDevice(addr: String, name: String): SourceDevice = mockk {
        every { this@mockk.address } returns addr
        every { label } returns name
        every { deviceType } returns SourceDevice.Type.HEADPHONES
        every { getStreamId(AudioStream.Type.MUSIC) } returns AudioStream.Id.STREAM_MUSIC
        every { getStreamId(AudioStream.Type.CALL) } returns AudioStream.Id.STREAM_VOICE_CALL
        every { getStreamId(AudioStream.Type.RINGTONE) } returns AudioStream.Id.STREAM_RINGTONE
        every { getStreamId(AudioStream.Type.NOTIFICATION) } returns AudioStream.Id.STREAM_NOTIFICATION
        every { getStreamId(AudioStream.Type.ALARM) } returns AudioStream.Id.STREAM_ALARM
    }

    @Test
    fun `two stable devices with different names - only owner gets write`() = runTest {
        val module = createModule()
        val stableTime = System.currentTimeMillis() - 60_000L

        val dev1 = makeSourceDevice("AA:BB:CC:DD:EE:01", "AirPods")
        val dev2 = makeSourceDevice("AA:BB:CC:DD:EE:02", "Speaker")
        val cfg1 = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:01", musicVolume = 0.5f, volumeObserving = true, lastConnected = stableTime)
        val cfg2 = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:02", musicVolume = 0.3f, volumeObserving = true, lastConnected = stableTime)
        devicesFlow.value = listOf(
            ManagedDevice(isConnected = true, device = dev1, config = cfg1),
            ManagedDevice(isConnected = true, device = dev2, config = cfg2),
        )

        // Register with different connect times — dev2 is the latest → owner
        ownerRegistry.onDeviceConnected("AA:BB:CC:DD:EE:01", "AirPods", SourceDevice.Type.HEADPHONES, 1000L, 0L)
        ownerRegistry.onDeviceConnected("AA:BB:CC:DD:EE:02", "Speaker", SourceDevice.Type.HEADPHONES, 2000L, 1L)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        every { volumeTool.getVolumePercentage(AudioStream.Id.STREAM_MUSIC) } returns 0.7f
        every { volumeTool.wasUs(any(), any()) } returns false

        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, 5, 11, self = false))

        coVerify(exactly = 0) { deviceRepo.updateDevice("AA:BB:CC:DD:EE:01", any()) }
        coVerify(exactly = 1) { deviceRepo.updateDevice("AA:BB:CC:DD:EE:02", any()) }
    }

    @Test
    fun `two stable devices with same name - both get writes (grouped earbuds)`() = runTest {
        val module = createModule()
        val stableTime = System.currentTimeMillis() - 60_000L

        val dev1 = makeSourceDevice("AA:BB:CC:DD:EE:01", "Buds3 Pro")
        val dev2 = makeSourceDevice("AA:BB:CC:DD:EE:02", "Buds3 Pro")
        val cfg1 = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:01", musicVolume = 0.5f, volumeObserving = true, lastConnected = stableTime)
        val cfg2 = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:02", musicVolume = 0.5f, volumeObserving = true, lastConnected = stableTime)
        devicesFlow.value = listOf(
            ManagedDevice(isConnected = true, device = dev1, config = cfg1),
            ManagedDevice(isConnected = true, device = dev2, config = cfg2),
        )

        // Same name + type + within 10s → grouped → both are owner
        ownerRegistry.onDeviceConnected("AA:BB:CC:DD:EE:01", "Buds3 Pro", SourceDevice.Type.HEADPHONES, 1000L, 0L)
        ownerRegistry.onDeviceConnected("AA:BB:CC:DD:EE:02", "Buds3 Pro", SourceDevice.Type.HEADPHONES, 1002L, 1L)

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        every { volumeTool.getVolumePercentage(AudioStream.Id.STREAM_MUSIC) } returns 0.7f
        every { volumeTool.wasUs(any(), any()) } returns false

        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, 5, 11, self = false))

        coVerify(exactly = 1) { deviceRepo.updateDevice("AA:BB:CC:DD:EE:01", any()) }
        coVerify(exactly = 1) { deviceRepo.updateDevice("AA:BB:CC:DD:EE:02", any()) }
    }

    @Test
    fun `one stabilizing and one stable - no writes`() = runTest {
        val module = createModule()
        val stableTime = System.currentTimeMillis() - 60_000L
        val recentTime = System.currentTimeMillis()

        val dev1 = makeSourceDevice("AA:BB:CC:DD:EE:01", "AirPods")
        val dev2 = makeSourceDevice("AA:BB:CC:DD:EE:02", "Speaker")
        val cfg1 = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:01", musicVolume = 0.5f, volumeObserving = true, lastConnected = stableTime)
        val cfg2 = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:02", musicVolume = 0.3f, volumeObserving = true, lastConnected = recentTime)
        devicesFlow.value = listOf(
            ManagedDevice(isConnected = true, device = dev1, config = cfg1),
            ManagedDevice(isConnected = true, device = dev2, config = cfg2),
        )

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
        every { volumeTool.getVolumePercentage(AudioStream.Id.STREAM_MUSIC) } returns 0.7f
        every { volumeTool.wasUs(any(), any()) } returns false

        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, 5, 11, self = false))

        coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
    }
}
