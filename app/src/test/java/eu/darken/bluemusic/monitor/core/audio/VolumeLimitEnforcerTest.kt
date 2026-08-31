package eu.darken.bluemusic.monitor.core.audio

import android.media.AudioManager
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.SourceDeviceWrapper
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class VolumeLimitEnforcerTest : BaseTest() {

    private val address = "AA:BB:CC:DD:EE:FF"
    private val streamId = AudioStream.Id.STREAM_MUSIC
    private val maxLevel = 15

    private lateinit var audioManager: AudioManager
    private lateinit var audioLevels: MutableMap<AudioStream.Id, Int>
    private lateinit var volumeTool: VolumeTool
    private lateinit var enforcer: VolumeLimitEnforcer

    @BeforeEach
    fun setup() {
        audioManager = mockk(relaxed = true)
        audioLevels = AudioStream.Id.entries.associateWith { 0 }.toMutableMap()
        every { audioManager.getStreamMaxVolume(any()) } returns maxLevel
        every { audioManager.getStreamVolume(any()) } answers { audioLevels[toStreamId(firstArg())] ?: 0 }
        every { audioManager.setStreamVolume(any(), any(), any()) } answers {
            audioLevels[toStreamId(firstArg())] = secondArg()
        }
        volumeTool = VolumeTool(audioManager).apply { clock = { 1000L } }
        enforcer = VolumeLimitEnforcer(volumeTool)
    }

    private fun device(
        addr: String = address,
        label: String = "Test Device",
        connected: Boolean = true,
        isEnabled: Boolean = true,
        musicVolume: Float? = 0.5f,
        volumeLimit: Boolean = true,
        musicVolumeMin: Float? = null,
        musicVolumeMax: Float? = null,
    ) = ManagedDevice(
        isConnected = connected,
        device = SourceDeviceWrapper(
            address = addr,
            alias = label,
            name = label,
            deviceType = SourceDevice.Type.HEADPHONES,
            isConnected = connected,
        ),
        config = DeviceConfigEntity(
            address = addr,
            musicVolume = musicVolume,
            volumeLimit = volumeLimit,
            musicVolumeMin = musicVolumeMin,
            musicVolumeMax = musicVolumeMax,
            isEnabled = isEnabled,
        ),
    )

    @Test
    fun `a level inside the band is left alone`() = runTest {
        audioLevels[streamId] = 5
        val dev = device(musicVolumeMin = 0.2f, musicVolumeMax = 0.5f)

        enforcer.enforce(streamId, listOf(dev), setOf(address)) shouldBe false
        audioLevels[streamId] shouldBe 5
    }

    @Test
    fun `a level above the maximum is corrected down to it`() = runTest {
        audioLevels[streamId] = 15
        val dev = device(musicVolumeMax = 0.5f)

        enforcer.enforce(streamId, listOf(dev), setOf(address)) shouldBe true
        audioLevels[streamId] shouldBe 7
    }

    @Test
    fun `a level below the minimum is corrected up to it`() = runTest {
        audioLevels[streamId] = 1
        val dev = device(musicVolumeMin = 0.4f)

        enforcer.enforce(streamId, listOf(dev), setOf(address)) shouldBe true
        audioLevels[streamId] shouldBe 6
    }

    @Test
    fun `without owners nothing is enforced`() = runTest {
        audioLevels[streamId] = 15
        val dev = device(musicVolumeMax = 0.5f)

        enforcer.enforce(streamId, listOf(dev), emptySet()) shouldBe false
        audioLevels[streamId] shouldBe 15
    }

    @Test
    fun `a non-owning device does not contribute a band`() = runTest {
        audioLevels[streamId] = 15
        val other = device(addr = "11:22:33:44:55:66", musicVolumeMax = 0.5f)

        enforcer.enforce(streamId, listOf(other), setOf(address)) shouldBe false
        audioLevels[streamId] shouldBe 15
    }

    @Test
    fun `an inactive device does not contribute a band`() = runTest {
        audioLevels[streamId] = 15
        val dev = device(connected = false, musicVolumeMax = 0.5f)

        enforcer.enforce(streamId, listOf(dev), setOf(address)) shouldBe false
        audioLevels[streamId] shouldBe 15
    }

    @Test
    fun `an unmanaged stream is not bounded`() = runTest {
        audioLevels[streamId] = 15
        val dev = device(musicVolume = null, musicVolumeMax = 0.5f)

        enforcer.enforce(streamId, listOf(dev), setOf(address)) shouldBe false
        audioLevels[streamId] shouldBe 15
    }

    @Test
    fun `the strictest bound of the owner group governs the stream`() = runTest {
        audioLevels[streamId] = 15
        val left = device(addr = address, musicVolumeMax = 0.5f)
        val right = device(addr = "11:22:33:44:55:66", musicVolumeMax = 0.3f)

        enforcer.enforce(streamId, listOf(left, right), setOf(address, "11:22:33:44:55:66")) shouldBe true

        audioLevels[streamId] shouldBe 4
    }

    @Test
    fun `disjoint bounds in the owner group resolve to the maximum`() = runTest {
        audioLevels[streamId] = 15
        val capped = device(addr = address, musicVolumeMax = 0.2f)
        val floored = device(addr = "11:22:33:44:55:66", musicVolumeMin = 0.8f)

        enforcer.enforce(streamId, listOf(capped, floored), setOf(address, "11:22:33:44:55:66")) shouldBe true

        audioLevels[streamId] shouldBe 3
    }

    @Test
    fun `the enforcer and resolveBoundedLevel agree on the same input`() = runTest {
        val band = VolumeBand(min = 0.2f, max = 0.5f)
        val dev = device(musicVolume = 1f, musicVolumeMin = band.min, musicVolumeMax = band.max)
        audioLevels[streamId] = 15

        enforcer.enforce(streamId, listOf(dev), setOf(address)) shouldBe true

        audioLevels[streamId] shouldBe volumeTool.resolveBoundedLevel(streamId, 1f, band)
    }

    private fun toStreamId(id: Int): AudioStream.Id = AudioStream.Id.entries.first { it.id == id }
}
