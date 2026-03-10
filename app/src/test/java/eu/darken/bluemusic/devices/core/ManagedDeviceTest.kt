package eu.darken.bluemusic.devices.core

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration

class ManagedDeviceTest : BaseTest() {

    private lateinit var sourceDevice: SourceDevice

    @BeforeEach
    fun setup() {
        sourceDevice = mockk {
            every { address } returns "AA:BB:CC:DD:EE:FF"
            every { label } returns "Device Label"
            every { deviceType } returns SourceDevice.Type.HEADPHONES
            every { getStreamId(AudioStream.Type.MUSIC) } returns AudioStream.Id.STREAM_MUSIC
            every { getStreamId(AudioStream.Type.CALL) } returns AudioStream.Id.STREAM_VOICE_CALL
            every { getStreamId(AudioStream.Type.RINGTONE) } returns AudioStream.Id.STREAM_RINGTONE
            every { getStreamId(AudioStream.Type.NOTIFICATION) } returns AudioStream.Id.STREAM_NOTIFICATION
            every { getStreamId(AudioStream.Type.ALARM) } returns AudioStream.Id.STREAM_ALARM
        }
    }

    private fun create(
        config: DeviceConfigEntity = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:FF"),
        connected: Boolean = true,
    ) = ManagedDevice(
        isConnected = connected,
        device = sourceDevice,
        config = config,
    )

    // region isActive

    @Test
    fun `isActive - connected and enabled`() {
        create(config = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:FF", isEnabled = true), connected = true)
            .isActive shouldBe true
    }

    @Test
    fun `isActive - connected but disabled`() {
        create(config = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:FF", isEnabled = false), connected = true)
            .isActive shouldBe false
    }

    @Test
    fun `isActive - disconnected but enabled`() {
        create(config = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:FF", isEnabled = true), connected = false)
            .isActive shouldBe false
    }

    @Test
    fun `isActive - disconnected and disabled`() {
        create(config = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:FF", isEnabled = false), connected = false)
            .isActive shouldBe false
    }

    // endregion

    // region requiresMonitor

    @Test
    fun `requiresMonitor - all false`() {
        create().requiresMonitor shouldBe false
    }

    @Test
    fun `requiresMonitor - volumeLock true`() {
        create(config = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:FF", volumeLock = true))
            .requiresMonitor shouldBe true
    }

    @Test
    fun `requiresMonitor - volumeObserving true`() {
        create(config = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:FF", volumeObserving = true))
            .requiresMonitor shouldBe true
    }

    @Test
    fun `requiresMonitor - volumeRateLimiter true`() {
        create(config = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:FF", volumeRateLimiter = true))
            .requiresMonitor shouldBe true
    }

    // endregion

    // region getVolume

    @Test
    fun `getVolume maps each type to correct config field`() {
        val config = DeviceConfigEntity(
            address = "AA:BB:CC:DD:EE:FF",
            musicVolume = 0.1f,
            callVolume = 0.2f,
            ringVolume = 0.3f,
            notificationVolume = 0.4f,
            alarmVolume = 0.5f,
        )
        val device = create(config = config)

        device.getVolume(AudioStream.Type.MUSIC) shouldBe 0.1f
        device.getVolume(AudioStream.Type.CALL) shouldBe 0.2f
        device.getVolume(AudioStream.Type.RINGTONE) shouldBe 0.3f
        device.getVolume(AudioStream.Type.NOTIFICATION) shouldBe 0.4f
        device.getVolume(AudioStream.Type.ALARM) shouldBe 0.5f
    }

    @Test
    fun `getVolume returns null when not set`() {
        val device = create()
        AudioStream.Type.entries.forEach { type ->
            device.getVolume(type) shouldBe null
        }
    }

    // endregion

    // region getStreamType

    @Test
    fun `getStreamType reverse lookup returns correct type`() {
        val device = create()
        device.getStreamType(AudioStream.Id.STREAM_MUSIC) shouldBe AudioStream.Type.MUSIC
        device.getStreamType(AudioStream.Id.STREAM_VOICE_CALL) shouldBe AudioStream.Type.CALL
        device.getStreamType(AudioStream.Id.STREAM_RINGTONE) shouldBe AudioStream.Type.RINGTONE
        device.getStreamType(AudioStream.Id.STREAM_NOTIFICATION) shouldBe AudioStream.Type.NOTIFICATION
        device.getStreamType(AudioStream.Id.STREAM_ALARM) shouldBe AudioStream.Type.ALARM
    }

    @Test
    fun `getStreamType returns null for unknown id`() {
        val device = create()
        device.getStreamType(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE) shouldBe null
    }

    // endregion

    // region label

    @Test
    fun `label uses customName when present`() {
        create(config = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:FF", customName = "My Speaker"))
            .label shouldBe "My Speaker"
    }

    @Test
    fun `label falls back to device label when customName is null`() {
        create(config = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:FF", customName = null))
            .label shouldBe "Device Label"
    }

    // endregion

    // region duration defaults

    @Test
    fun `monitoringDuration defaults to 4s when null`() {
        create().monitoringDuration shouldBe Duration.ofSeconds(4)
    }

    @Test
    fun `adjustmentDelay defaults to 250ms when null`() {
        create().adjustmentDelay shouldBe Duration.ofMillis(250)
    }

    @Test
    fun `actionDelay defaults to 4s when null`() {
        create().actionDelay shouldBe Duration.ofSeconds(4)
    }

    @Test
    fun `monitoringDuration uses config value when set`() {
        create(config = DeviceConfigEntity(address = "AA:BB:CC:DD:EE:FF", monitoringDuration = 10000L))
            .monitoringDuration shouldBe Duration.ofSeconds(10)
    }

    // endregion
}
