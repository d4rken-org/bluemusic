package eu.darken.bluemusic.monitor.core.modules.volume

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class VolumeRateLimiterModuleTest : BaseTest() {

    private val address = "AA:BB:CC:DD:EE:FF"

    private lateinit var volumeTool: VolumeTool
    private lateinit var deviceRepo: DeviceRepo
    private lateinit var ownerRegistry: eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
    private lateinit var sourceDevice: SourceDevice
    private lateinit var devicesFlow: MutableStateFlow<List<ManagedDevice>>

    @BeforeEach
    fun setup() {
        volumeTool = mockk(relaxed = true)
        deviceRepo = mockk(relaxed = true)
        ownerRegistry = eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry()
        devicesFlow = MutableStateFlow(emptyList())
        every { deviceRepo.devices } returns devicesFlow

        sourceDevice = mockk {
            every { this@mockk.address } returns this@VolumeRateLimiterModuleTest.address
            every { label } returns "Test Device"
            every { deviceType } returns SourceDevice.Type.HEADPHONES
            every { getStreamId(AudioStream.Type.MUSIC) } returns AudioStream.Id.STREAM_MUSIC
            every { getStreamId(AudioStream.Type.CALL) } returns AudioStream.Id.STREAM_VOICE_CALL
            every { getStreamId(AudioStream.Type.RINGTONE) } returns AudioStream.Id.STREAM_RINGTONE
            every { getStreamId(AudioStream.Type.NOTIFICATION) } returns AudioStream.Id.STREAM_NOTIFICATION
            every { getStreamId(AudioStream.Type.ALARM) } returns AudioStream.Id.STREAM_ALARM
        }
    }

    private fun createModule() = VolumeRateLimiterModule(
        volumeTool = volumeTool,
        deviceRepo = deviceRepo,
        ownerRegistry = ownerRegistry,
    )

    private fun config(
        volumeRateLimiter: Boolean = true,
        isEnabled: Boolean = true,
        musicVolume: Float? = null,
        volumeRateLimitIncreaseMs: Long? = null,
        volumeRateLimitDecreaseMs: Long? = null,
    ): DeviceConfigEntity = DeviceConfigEntity(
        address = address,
        volumeRateLimiter = volumeRateLimiter,
        isEnabled = isEnabled,
        musicVolume = musicVolume,
        volumeRateLimitIncreaseMs = volumeRateLimitIncreaseMs,
        volumeRateLimitDecreaseMs = volumeRateLimitDecreaseMs,
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

    @Test
    fun `volume change within rate window is reverted`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        seedActive(managedDevice(cfg))

        every { volumeTool.wasUs(any(), any()) } returns false
        coEvery { volumeTool.changeVolume(streamId = any(), targetLevel = any()) } returns true

        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 6, self = false))
        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 6, newVolume = 10, self = false))

        coVerify { volumeTool.changeVolume(streamId = AudioStream.Id.STREAM_MUSIC, targetLevel = 6) }
    }

    @Test
    fun `first event bypasses rate limiting and applies step limit`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        seedActive(managedDevice(cfg))

        every { volumeTool.wasUs(any(), any()) } returns false
        coEvery { volumeTool.changeVolume(streamId = any(), targetLevel = any()) } returns true

        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 8, self = false))

        coVerify { volumeTool.changeVolume(streamId = AudioStream.Id.STREAM_MUSIC, targetLevel = 6) }
    }

    @Test
    fun `step limiting clamps increase of 2 to 1`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        seedActive(managedDevice(cfg))

        every { volumeTool.wasUs(any(), any()) } returns false
        coEvery { volumeTool.changeVolume(streamId = any(), targetLevel = any()) } returns true

        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 7, self = false))

        coVerify { volumeTool.changeVolume(streamId = AudioStream.Id.STREAM_MUSIC, targetLevel = 6) }
    }

    @Test
    fun `step limiting clamps decrease of 2 to 1`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        seedActive(managedDevice(cfg))

        every { volumeTool.wasUs(any(), any()) } returns false
        coEvery { volumeTool.changeVolume(streamId = any(), targetLevel = any()) } returns true

        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 10, newVolume = 8, self = false))

        coVerify { volumeTool.changeVolume(streamId = AudioStream.Id.STREAM_MUSIC, targetLevel = 9) }
    }

    @Test
    fun `self-triggered change updates reference without reverting`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        seedActive(managedDevice(cfg))

        every { volumeTool.wasUs(AudioStream.Id.STREAM_MUSIC, 8) } returns true

        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 8, self = false))

        coVerify(exactly = 0) { volumeTool.changeVolume(streamId = any(), targetLevel = any()) }
    }

    @Test
    fun `state keyed by streamId only`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        seedActive(managedDevice(cfg))

        every { volumeTool.wasUs(any(), any()) } returns false
        coEvery { volumeTool.changeVolume(streamId = any(), targetLevel = any()) } returns true

        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 8, self = false))

        module.handle(VolumeEvent(AudioStream.Id.STREAM_RINGTONE, oldVolume = 5, newVolume = 8, self = false))

        coVerify { volumeTool.changeVolume(streamId = AudioStream.Id.STREAM_MUSIC, targetLevel = 6) }
        coVerify { volumeTool.changeVolume(streamId = AudioStream.Id.STREAM_RINGTONE, targetLevel = 6) }
    }

    @Test
    fun `rate limiting only applied to owner group devices`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        seedActive(managedDevice(cfg))

        // Add a second non-owner device with rate limiter enabled
        val secondAddr = "BB:CC:DD:EE:FF:00"
        val secondSource: SourceDevice = mockk {
            every { this@mockk.address } returns secondAddr
            every { label } returns "Other Device"
            every { deviceType } returns SourceDevice.Type.HEADPHONES
            every { getStreamId(AudioStream.Type.MUSIC) } returns AudioStream.Id.STREAM_MUSIC
        }
        val secondCfg = DeviceConfigEntity(address = secondAddr, volumeRateLimiter = true, isEnabled = true, musicVolume = 0.5f)
        devicesFlow.value = devicesFlow.value + ManagedDevice(isConnected = true, device = secondSource, config = secondCfg)
        // Don't register secondAddr in registry — it's not an owner

        every { volumeTool.wasUs(any(), any()) } returns false
        coEvery { volumeTool.changeVolume(streamId = any(), targetLevel = any()) } returns true

        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 8, self = false))

        // Rate limiting applied (step clamp from 5→8 to 5→6), proving the first device IS eligible
        coVerify { volumeTool.changeVolume(streamId = AudioStream.Id.STREAM_MUSIC, targetLevel = 6) }
    }

    @Test
    fun `ownership change clears rate limiter state`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        seedActive(managedDevice(cfg))

        every { volumeTool.wasUs(any(), any()) } returns false
        coEvery { volumeTool.changeVolume(streamId = any(), targetLevel = any()) } returns true

        // First event sets up state
        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 6, self = false))

        // Change ownership by connecting a new device that becomes owner
        ownerRegistry.onDeviceConnected("NEW:DEVICE:00:00:00:01", "NewOwner", SourceDevice.Type.HEADPHONES, 9000L, 10L)

        // New event after ownership change — state should have been cleared
        // Since state is cleared, the new event is treated as initial (no rate limit applied, but step limit applies)
        val newSource: SourceDevice = mockk {
            every { this@mockk.address } returns "NEW:DEVICE:00:00:00:01"
            every { label } returns "NewOwner"
            every { deviceType } returns SourceDevice.Type.HEADPHONES
            every { getStreamId(AudioStream.Type.MUSIC) } returns AudioStream.Id.STREAM_MUSIC
        }
        val newCfg = DeviceConfigEntity(address = "NEW:DEVICE:00:00:00:01", volumeRateLimiter = true, isEnabled = true, musicVolume = 0.5f)
        devicesFlow.value = listOf(ManagedDevice(isConnected = true, device = newSource, config = newCfg))

        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 8, self = false))

        // Step limit clamps 5→8 to 5→6 (fresh state, no rate window carry-over)
        coVerify { volumeTool.changeVolume(streamId = AudioStream.Id.STREAM_MUSIC, targetLevel = 6) }
    }

    @Test
    fun `new owner does not inherit previous owner reference volume`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        seedActive(managedDevice(cfg))

        every { volumeTool.wasUs(any(), any()) } returns false
        coEvery { volumeTool.changeVolume(streamId = any(), targetLevel = any()) } returns true

        // Establish reference at volume 10
        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 9, newVolume = 10, self = false))

        // Change ownership
        ownerRegistry.onDeviceConnected("NEW:DEVICE:00:00:00:01", "NewOwner", SourceDevice.Type.HEADPHONES, 9000L, 10L)
        val newSource: SourceDevice = mockk {
            every { this@mockk.address } returns "NEW:DEVICE:00:00:00:01"
            every { label } returns "NewOwner"
            every { deviceType } returns SourceDevice.Type.HEADPHONES
            every { getStreamId(AudioStream.Type.MUSIC) } returns AudioStream.Id.STREAM_MUSIC
        }
        devicesFlow.value = listOf(ManagedDevice(isConnected = true, device = newSource, config = DeviceConfigEntity(
            address = "NEW:DEVICE:00:00:00:01", volumeRateLimiter = true, isEnabled = true, musicVolume = 0.5f,
        )))

        // New owner event: old→5, new→6. Without state clear, this would be
        // compared against reference=10 and rate-limited. With cleared state,
        // it's treated as initial with step limit only.
        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 6, self = false))

        // Volume 6 is only 1 step from old=5, so no clamping needed — should be accepted
        // (no changeVolume call means it was accepted as-is)
        coVerify(exactly = 0) { volumeTool.changeVolume(streamId = AudioStream.Id.STREAM_MUSIC, targetLevel = 5) }
    }

    @Test
    fun `state keyed includes ownerKey - different owners get separate state`() = runTest {
        val module = createModule()
        val cfg = config(musicVolume = 0.5f)
        seedActive(managedDevice(cfg))

        every { volumeTool.wasUs(any(), any()) } returns false
        coEvery { volumeTool.changeVolume(streamId = any(), targetLevel = any()) } returns true

        // First owner: establish reference
        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 6, self = false))

        // Switch owner
        ownerRegistry.onDeviceConnected("NEW:DEVICE:00:00:00:01", "NewOwner", SourceDevice.Type.HEADPHONES, 9000L, 10L)
        val newSource: SourceDevice = mockk {
            every { this@mockk.address } returns "NEW:DEVICE:00:00:00:01"
            every { label } returns "NewOwner"
            every { deviceType } returns SourceDevice.Type.HEADPHONES
            every { getStreamId(AudioStream.Type.MUSIC) } returns AudioStream.Id.STREAM_MUSIC
        }
        devicesFlow.value = listOf(ManagedDevice(isConnected = true, device = newSource, config = DeviceConfigEntity(
            address = "NEW:DEVICE:00:00:00:01", volumeRateLimiter = true, isEnabled = true, musicVolume = 0.5f,
        )))

        // New owner gets a fresh start — 3→7 triggers step limit (clamp to +1)
        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 3, newVolume = 7, self = false))

        // Should clamp to 3+1=4, proving new owner has fresh state (reference=3)
        coVerify { volumeTool.changeVolume(streamId = AudioStream.Id.STREAM_MUSIC, targetLevel = 4) }
    }

    @Test
    fun `no eligible devices is no-op`() = runTest {
        val module = createModule()
        val cfg = config(volumeRateLimiter = false)
        seedActive(managedDevice(cfg))

        every { volumeTool.wasUs(any(), any()) } returns false

        module.handle(VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = 10, self = false))

        coVerify(exactly = 0) { volumeTool.changeVolume(streamId = any(), targetLevel = any()) }
    }
}
