package eu.darken.bluemusic.monitor.core.modules.volume

import android.media.AudioManager
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.RingerMode
import eu.darken.bluemusic.monitor.core.audio.RingerTool
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeLimitEnforcer
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.audio.levelToPercentage
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.audio.normalRingerTool

class VolumeUpdateModuleTest : BaseTest() {

    private val address = "AA:BB:CC:DD:EE:FF"

    private lateinit var volumeTool: VolumeTool
    private lateinit var limitEnforcer: VolumeLimitEnforcer
    private lateinit var ringerTool: RingerTool
    private lateinit var deviceRepo: DeviceRepo
    private lateinit var observationGate: VolumeObservationGate
    private lateinit var ownerRegistry: eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
    private lateinit var sourceDevice: SourceDevice
    private lateinit var devicesFlow: MutableStateFlow<List<ManagedDevice>>

    private val unknownRoute = VolumeTool.MediaRoute(
        isBluetooth = null,
        addresses = emptySet(),
        description = "availableOnly=[none]",
    )
    private val speakerRoute = VolumeTool.MediaRoute(
        isBluetooth = false,
        addresses = emptySet(),
        description = "predicted=[SPEAKER#2]",
    )

    private fun btRoute(vararg addresses: String) = VolumeTool.MediaRoute(
        isBluetooth = true,
        addresses = addresses.toSet(),
        description = "predicted=[BT_A2DP#8]",
    )

    @BeforeEach
    fun setup() {
        volumeTool = mockk(relaxed = true)
        // The enforcer needs real level math; the module's own VolumeTool stays a mock so the
        // tests can control what a live hardware read would return.
        limitEnforcer = VolumeLimitEnforcer(
            VolumeTool(mockk<AudioManager>(relaxed = true).also {
                every { it.getStreamMaxVolume(any()) } returns 15
            }),
            normalRingerTool(),
        )
        ringerTool = mockk(relaxed = true)
        deviceRepo = mockk(relaxed = true)
        observationGate = VolumeObservationGate()
        ownerRegistry = eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry()
        devicesFlow = MutableStateFlow(emptyList())
        every { deviceRepo.devices } returns devicesFlow
        coEvery { deviceRepo.updateDevice(any(), any()) } just Runs
        every { volumeTool.getMinVolume(any()) } returns 0
        every { volumeTool.getMaxVolume(any()) } returns 15
        every { volumeTool.queryActiveMediaRoute() } returns unknownRoute

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
        limitEnforcer = limitEnforcer,
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
        volumeRateLimiter: Boolean = false,
        volumeLimit: Boolean = false,
        musicVolumeMax: Float? = null,
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
        volumeRateLimiter = volumeRateLimiter,
        volumeLimit = volumeLimit,
        musicVolumeMax = musicVolumeMax,
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

    /**
     * When a rate-limiter-eligible owner exists, the module persists the live
     * hardware level instead of the event's value. Default: hardware matches
     * what the event reported; tests for the rate-limiter interplay pass a
     * diverging [hardwareLevel].
     */
    private suspend fun handleObserved(
        module: VolumeUpdateModule,
        event: VolumeEvent,
        hardwareLevel: Int = event.newVolume,
    ) {
        every { volumeTool.getCurrentVolume(event.streamId) } returns hardwareLevel
        module.handle(event)
    }

    private suspend fun runTransform(
        module: VolumeUpdateModule,
        event: VolumeEvent,
        seedConfig: DeviceConfigEntity,
        hardwareLevel: Int = event.newVolume,
    ): DeviceConfigEntity {
        val slot = slot<(DeviceConfigEntity) -> DeviceConfigEntity>()
        coEvery { deviceRepo.updateDevice(address, capture(slot)) } just Runs
        handleObserved(module, event, hardwareLevel)
        return slot.captured(seedConfig)
    }

    // ------------------------------------------------------------------------
    // self-classified events are ignored
    // ------------------------------------------------------------------------
    @Test
    fun `self-triggered events are ignored`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        seedActive(managedDevice(cfg))

        handleObserved(
            module,
            VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 11, self = true),
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

        observationGate.suppress(AudioStream.Id.STREAM_MUSIC)

        handleObserved(
            module,
            VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 11, self = false),
        )

        coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
    }

    @Test
    fun `volume changes for unsuppressed streams are persisted`() = runTest {
        val module = createModule()
        // stored 0.1 → level 2, event newVolume=11 → different level → writes
        val cfg = config(musicVolume = 0.1f)
        seedActive(managedDevice(cfg))

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL

        val token = observationGate.suppress(AudioStream.Id.STREAM_MUSIC)
        observationGate.unsuppress(token)

        handleObserved(
            module,
            VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 11, self = false),
        )

        coVerify(exactly = 1) { deviceRepo.updateDevice(any(), any()) }
    }

    @Test
    fun `mirrored stream suppression blocks BLUETOOTH_HANDSFREE when VOICE_CALL is suppressed`() = runTest {
        val module = createModule()
        val cfg = config(callVolume = 1.0f)
        seedActive(managedDevice(cfg))

        observationGate.suppress(AudioStream.Id.STREAM_VOICE_CALL)

        handleObserved(
            module,
            VolumeEvent(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, oldVolume = 15, newVolume = 11, self = false),
        )

        coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
    }

    // ------------------------------------------------------------------------
    // Normal ringer + MUSIC → writes percent
    // ------------------------------------------------------------------------
    @Test
    fun `normal ringer music change writes percent`() = runTest {
        val module = createModule()
        // stored 0.1 → level 2, event newVolume=8 → different level → writes
        val cfg = config(musicVolume = 0.1f)
        seedActive(managedDevice(cfg))

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL

        val result = runTransform(
            module,
            VolumeEvent(AudioStream.Id.STREAM_MUSIC, 11, 8, self = false),
            cfg,
        )

        // levelToPercentage(8, 0, 15) = 8/15
        result.musicVolume shouldBe levelToPercentage(8, 0, 15)
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

        handleObserved(
            module,
            VolumeEvent(AudioStream.Id.STREAM_NOTIFICATION, 1, 0, self = false),
        )

        coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
    }

    // ------------------------------------------------------------------------
    // NOTIFICATION in VIBRATE with hardware > 0 → captured (non-coupling device)
    // ------------------------------------------------------------------------
    @Test
    fun `vibrate ringer notification nonzero hardware captures change`() = runTest {
        val module = createModule()
        // stored 0.19 → level 3, event newVolume=5 → different level → writes
        val cfg = config(notificationVolume = 0.19f)
        seedActive(managedDevice(cfg))

        every { ringerTool.getCurrentRingerMode() } returns RingerMode.VIBRATE

        val result = runTransform(
            module,
            VolumeEvent(AudioStream.Id.STREAM_NOTIFICATION, 1, 5, self = false),
            cfg,
        )

        // levelToPercentage(5, 0, 15) = 5/15 = 1/3
        result.notificationVolume shouldBe levelToPercentage(5, 0, 15)
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


        handleObserved(
            module,
            VolumeEvent(AudioStream.Id.STREAM_MUSIC, 11, 17, self = false),
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


        handleObserved(
            module,
            VolumeEvent(AudioStream.Id.STREAM_MUSIC, 11, 17, self = false),
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


        handleObserved(
            module,
            VolumeEvent(AudioStream.Id.STREAM_MUSIC, 11, 17, self = false),
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


        handleObserved(module, VolumeEvent(AudioStream.Id.STREAM_MUSIC, 5, 11, self = false))

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


        handleObserved(module, VolumeEvent(AudioStream.Id.STREAM_MUSIC, 5, 11, self = false))

        coVerify(exactly = 1) { deviceRepo.updateDevice("AA:BB:CC:DD:EE:01", any()) }
        coVerify(exactly = 0) { deviceRepo.updateDevice("AA:BB:CC:DD:EE:02", any()) }
    }

    // ------------------------------------------------------------------------
    // Multi-device characterization: documents current fan-out behavior
    // ------------------------------------------------------------------------

    private fun makeSourceDevice(
        addr: String,
        name: String,
        type: SourceDevice.Type = SourceDevice.Type.HEADPHONES,
    ): SourceDevice = mockk {
        every { this@mockk.address } returns addr
        every { label } returns name
        every { deviceType } returns type
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


        handleObserved(module, VolumeEvent(AudioStream.Id.STREAM_MUSIC, 5, 11, self = false))

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


        handleObserved(module, VolumeEvent(AudioStream.Id.STREAM_MUSIC, 5, 11, self = false))

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


        handleObserved(module, VolumeEvent(AudioStream.Id.STREAM_MUSIC, 5, 11, self = false))

        coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
    }

    // ------------------------------------------------------------------------
    // Level-equivalence: stored float maps to observed level → skip persist
    // ------------------------------------------------------------------------
    @Nested
    inner class LevelEquivalence {
        @Test
        fun `stored percentage maps to same level as observed — skips persist`() = runTest {
            val module = createModule()
            // 0.378 → percentageToLevel(0.378, 0, 15) = roundToInt(5.67) = 6
            val cfg = config(musicVolume = 0.378f)
            seedActive(managedDevice(cfg))

            every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
    
            handleObserved(
                module,
                VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 6, self = false),
            )

            coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
        }

        @Test
        fun `stored percentage maps to different level — persists new value`() = runTest {
            val module = createModule()
            // 0.378 → level 6, but observed level is 10 → different → writes
            val cfg = config(musicVolume = 0.378f)
            seedActive(managedDevice(cfg))

            every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
    
            val result = runTransform(
                module,
                VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 10, self = false),
                cfg,
            )

            result.musicVolume shouldBe levelToPercentage(10, 0, 15)
        }

        @Test
        fun `silent mode always persists regardless of level equivalence`() = runTest {
            val module = createModule()
            // Stored Normal percentage — ringer switched to SILENT → always writes sentinel
            val cfg = config(ringVolume = 0.4f)
            seedActive(managedDevice(cfg))

            every { ringerTool.getCurrentRingerMode() } returns RingerMode.SILENT
    
            val result = runTransform(
                module,
                VolumeEvent(AudioStream.Id.STREAM_RINGTONE, oldVolume = 6, newVolume = 0, self = false),
                cfg,
            )

            result.ringVolume shouldBe VolumeMode.LEGACY_SILENT_VALUE
        }

        @Test
        fun `two grouped devices — one equivalent skips, sibling with different stored value persists`() = runTest {
            val module = createModule()
            val stableTime = System.currentTimeMillis() - 60_000L

            val dev1 = makeSourceDevice("AA:BB:CC:DD:EE:01", "Buds3 Pro")
            val dev2 = makeSourceDevice("AA:BB:CC:DD:EE:02", "Buds3 Pro")
            // dev1: 0.378 → level 6, event newVolume=6 → same → skip
            // dev2: 0.1 → level 2, event newVolume=6 → different → write
            val cfg1 = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:01", musicVolume = 0.378f, volumeObserving = true, lastConnected = stableTime)
            val cfg2 = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:02", musicVolume = 0.1f, volumeObserving = true, lastConnected = stableTime)
            devicesFlow.value = listOf(
                ManagedDevice(isConnected = true, device = dev1, config = cfg1),
                ManagedDevice(isConnected = true, device = dev2, config = cfg2),
            )

            ownerRegistry.onDeviceConnected("AA:BB:CC:DD:EE:01", "Buds3 Pro", SourceDevice.Type.HEADPHONES, 1000L, 0L)
            ownerRegistry.onDeviceConnected("AA:BB:CC:DD:EE:02", "Buds3 Pro", SourceDevice.Type.HEADPHONES, 1002L, 1L)

            every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
    
            handleObserved(module, VolumeEvent(AudioStream.Id.STREAM_MUSIC, 5, 6, self = false))

            // dev1 skipped (equivalent), dev2 written (different level)
            coVerify(exactly = 0) { deviceRepo.updateDevice("AA:BB:CC:DD:EE:01", any()) }
            coVerify(exactly = 1) { deviceRepo.updateDevice("AA:BB:CC:DD:EE:02", any()) }
        }

        @Test
        fun `non-zero min stream range — equivalence check uses correct min`() = runTest {
            val module = createModule()
            // Simulate a stream with min=1, max=7 (e.g. some devices' ringtone)
            every { volumeTool.getMinVolume(AudioStream.Id.STREAM_MUSIC) } returns 1
            every { volumeTool.getMaxVolume(AudioStream.Id.STREAM_MUSIC) } returns 7

            // 0.5 → percentageToLevel(0.5, 1, 7) = (1 + 6*0.5).roundToInt() = 4
            val cfg = config(musicVolume = 0.5f)
            seedActive(managedDevice(cfg))

            every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL
    
            // event newVolume=4 → same as stored level → skip
            handleObserved(
                module,
                VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 3, newVolume = 4, self = false),
            )

            coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
        }
    }

    // ------------------------------------------------------------------------
    // Hardware readback: persist what's actually set, not what the event said
    //
    // Regression: with the rate limiter (priority 5) enabled, a Zello-style
    // volume jump is physically clamped before this module (priority 10) runs,
    // but the event still carries the original high value. Persisting the event
    // value stored the blocked jump and restored it on the next connect.
    //
    // Readback only happens when a rate-limiter-eligible owner exists for the
    // stream — otherwise the event's snapshot is kept, so a late live read
    // can't pick up an unrelated route change (issue #232).
    // ------------------------------------------------------------------------
    @Nested
    inner class HardwareReadback {
        @Test
        fun `persists hardware level when it differs from event volume`() = runTest {
            val module = createModule()
            // stored 0.1 → level 2; event reports jump to 15, but limiter clamped hardware to 6
            val cfg = config(musicVolume = 0.1f, volumeRateLimiter = true)
            seedActive(managedDevice(cfg))

            every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL

            val result = runTransform(
                module,
                VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 15, self = false),
                cfg,
                hardwareLevel = 6,
            )

            result.musicVolume shouldBe levelToPercentage(6, 0, 15)
        }

        @Test
        fun `skips persist when hardware was reverted to the stored level`() = runTest {
            val module = createModule()
            // stored maps to level 5; event reports jump to 15, but limiter reverted hardware to 5
            val cfg = config(musicVolume = levelToPercentage(5, 0, 15), volumeRateLimiter = true)
            seedActive(managedDevice(cfg))

            every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL

            handleObserved(
                module,
                VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 15, self = false),
                hardwareLevel = 5,
            )

            coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
        }

        @Test
        fun `notification zero-guard under vibrate uses hardware level`() = runTest {
            val module = createModule()
            // event says 5, but hardware reads 0 → ambiguous 0 under vibrate → preserve stored
            val cfg = config(notificationVolume = 0.19f, volumeRateLimiter = true)
            seedActive(managedDevice(cfg))

            every { ringerTool.getCurrentRingerMode() } returns RingerMode.VIBRATE

            handleObserved(
                module,
                VolumeEvent(AudioStream.Id.STREAM_NOTIFICATION, oldVolume = 1, newVolume = 5, self = false),
                hardwareLevel = 0,
            )

            coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
        }

        @Test
        fun `without eligible rate limiter the event volume is persisted, not the live read`() = runTest {
            val module = createModule()
            // No limiter on this device → no priority-5 writer, keep the event's snapshot
            // even when the live read disagrees (e.g. route already torn down, issue #232).
            val cfg = config(musicVolume = 0.1f, volumeRateLimiter = false)
            seedActive(managedDevice(cfg))

            every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL

            val result = runTransform(
                module,
                VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 11, self = false),
                cfg,
                hardwareLevel = 0,
            )

            result.musicVolume shouldBe levelToPercentage(11, 0, 15)
        }
    }

    // ------------------------------------------------------------------------
    // Route/owner agreement — the owner registry only sees ACL broadcasts, the
    // media route is the second opinion on who is actually playing.
    // ------------------------------------------------------------------------
    @Nested
    inner class RouteAgreement {

        private val speakerAddress = "00:00:00:00:00:00"

        @Test
        fun `music change is dropped when media no longer routes to the bluetooth owner`() = runTest {
            val module = createModule()
            val cfg = config(musicVolume = 0.1f)
            seedActive(managedDevice(cfg))

            every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL

            handleObserved(
                module,
                VolumeEvent(AudioStream.Id.STREAM_MUSIC, 5, 11, self = false, route = speakerRoute),
            )

            coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
        }

        @Test
        fun `music change persists when the route names the owner`() = runTest {
            val module = createModule()
            val cfg = config(musicVolume = 0.1f)
            seedActive(managedDevice(cfg))

            every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL

            val result = runTransform(
                module,
                VolumeEvent(AudioStream.Id.STREAM_MUSIC, 5, 11, self = false, route = btRoute(address)),
                cfg,
            )

            result.musicVolume shouldBe levelToPercentage(11, 0, 15)
        }

        @Test
        fun `music change persists when the route can not classify itself`() = runTest {
            val module = createModule()
            val cfg = config(musicVolume = 0.1f)
            seedActive(managedDevice(cfg))

            every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL

            handleObserved(
                module,
                VolumeEvent(AudioStream.Id.STREAM_MUSIC, 5, 11, self = false, route = unknownRoute),
            )

            coVerify(exactly = 1) { deviceRepo.updateDevice(address, any()) }
        }

        @Test
        fun `call change persists while media routes to the speaker`() = runTest {
            // HFP car kit with media audio disabled: it owns the call volume even
            // though music plays through the phone.
            val module = createModule()
            val cfg = config(callVolume = 0.1f)
            seedActive(managedDevice(cfg))

            every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL

            handleObserved(
                module,
                VolumeEvent(AudioStream.Id.STREAM_VOICE_CALL, 5, 11, self = false, route = speakerRoute),
            )

            coVerify(exactly = 1) { deviceRepo.updateDevice(address, any()) }
        }

        @Test
        fun `music change is dropped for the phone speaker while media routes to bluetooth`() = runTest {
            val module = createModule()
            seedSpeaker()

            every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL

            handleObserved(
                module,
                VolumeEvent(AudioStream.Id.STREAM_MUSIC, 5, 11, self = false, route = btRoute("AA:BB:CC:DD:EE:01")),
            )

            coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
        }

        @Test
        fun `music change persists for the phone speaker while media routes to the speaker`() = runTest {
            val module = createModule()
            seedSpeaker()

            every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL

            handleObserved(
                module,
                VolumeEvent(AudioStream.Id.STREAM_MUSIC, 5, 11, self = false, route = speakerRoute),
            )

            coVerify(exactly = 1) { deviceRepo.updateDevice(speakerAddress, any()) }
        }

        @Test
        fun `music change is dropped when the route still names the previous device`() = runTest {
            val module = createModule()
            val cfg = config(musicVolume = 0.1f)
            seedActive(managedDevice(cfg))
            // Handoff between two BVM-managed devices: the previous one is already
            // disconnected but the route still names it.
            val previousAddress = "AA:BB:CC:DD:EE:01"
            devicesFlow.value = devicesFlow.value + ManagedDevice(
                isConnected = false,
                device = makeSourceDevice(previousAddress, "Previous Device"),
                config = DeviceConfigEntity(address = previousAddress, musicVolume = 0.1f),
            )

            every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL

            handleObserved(
                module,
                VolumeEvent(AudioStream.Id.STREAM_MUSIC, 5, 11, self = false, route = btRoute(previousAddress)),
            )

            coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
        }

        @Test
        fun `music change persists when the route names no device BVM manages`() = runTest {
            val module = createModule()
            val cfg = config(musicVolume = 0.1f)
            seedActive(managedDevice(cfg))

            every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL

            handleObserved(
                module,
                VolumeEvent(AudioStream.Id.STREAM_MUSIC, 5, 11, self = false, route = btRoute("AA:BB:CC:DD:EE:01")),
            )

            coVerify(exactly = 1) { deviceRepo.updateDevice(address, any()) }
        }

        private suspend fun seedSpeaker() {
            val speaker = makeSourceDevice(speakerAddress, "Phone speaker", SourceDevice.Type.PHONE_SPEAKER)
            val cfg = DeviceConfigEntity(
                address = speakerAddress,
                musicVolume = 0.1f,
                volumeObserving = true,
                lastConnected = 0L,
            )
            devicesFlow.value = listOf(ManagedDevice(isConnected = true, device = speaker, config = cfg))
            ownerRegistry.onDeviceConnected(
                address = speakerAddress,
                label = "Phone speaker",
                deviceType = SourceDevice.Type.PHONE_SPEAKER,
                receivedAtElapsedMs = 1000L,
                sequence = 0L,
            )
        }
    }
    // ------------------------------------------------------------------------
    // Volume limit band: a cap is derivable from the event, so it never needs a
    // live hardware read (issue #232).
    // ------------------------------------------------------------------------
    @Nested
    inner class VolumeLimitBand {
        @Test
        fun `an in-band event keeps the event snapshot instead of reading the hardware`() = runTest {
            val module = createModule()
            // cap 0.8 -> levels 0..12, so the event's 11 is inside the band
            val cfg = config(musicVolume = 0.1f, volumeLimit = true, musicVolumeMax = 0.8f)
            seedActive(managedDevice(cfg))

            every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL

            val result = runTransform(
                module,
                VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 11, self = false),
                cfg,
                // What a live read would return if the route had already torn down.
                hardwareLevel = 0,
            )

            result.musicVolume shouldBe levelToPercentage(11, 0, 15)
            verify(exactly = 0) { volumeTool.getCurrentVolume(AudioStream.Id.STREAM_MUSIC) }
        }

        @Test
        fun `an out-of-band event persists the capped level without a live read`() = runTest {
            val module = createModule()
            // cap 0.5 -> levels 0..7, so the event's 15 is above the ceiling
            val cfg = config(musicVolume = 0.1f, volumeLimit = true, musicVolumeMax = 0.5f)
            seedActive(managedDevice(cfg))

            every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL

            val result = runTransform(
                module,
                VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 15, self = false),
                cfg,
                hardwareLevel = 0,
            )

            result.musicVolume shouldBe levelToPercentage(7, 0, 15)
            verify(exactly = 0) { volumeTool.getCurrentVolume(AudioStream.Id.STREAM_MUSIC) }
        }

        @Test
        fun `an eligible rate limiter still takes the live read on a capped stream`() = runTest {
            val module = createModule()
            val cfg = config(
                musicVolume = 0.1f,
                volumeRateLimiter = true,
                volumeLimit = true,
                musicVolumeMax = 0.5f,
            )
            seedActive(managedDevice(cfg))

            every { ringerTool.getCurrentRingerMode() } returns RingerMode.NORMAL

            val result = runTransform(
                module,
                VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 15, self = false),
                cfg,
                hardwareLevel = 6,
            )

            result.musicVolume shouldBe levelToPercentage(6, 0, 15)
        }
    }
}
