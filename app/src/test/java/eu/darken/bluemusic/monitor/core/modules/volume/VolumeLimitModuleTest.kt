package eu.darken.bluemusic.monitor.core.modules.volume

import android.media.AudioManager
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.SourceDeviceWrapper
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeLimitEnforcer
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.audio.normalRingerTool

class VolumeLimitModuleTest : BaseTest() {

    private val address = "AA:BB:CC:DD:EE:FF"
    private val siblingAddress = "11:22:33:44:55:66"
    private val maxLevel = 15

    private lateinit var audioManager: AudioManager
    private lateinit var audioLevels: MutableMap<AudioStream.Id, Int>
    private lateinit var volumeTool: VolumeTool
    private lateinit var deviceRepo: DeviceRepo
    private lateinit var ownerRegistry: AudioStreamOwnerRegistry
    private lateinit var devicesFlow: MutableStateFlow<List<ManagedDevice>>

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

        deviceRepo = mockk(relaxed = true)
        devicesFlow = MutableStateFlow(emptyList())
        every { deviceRepo.devices } returns devicesFlow

        ownerRegistry = AudioStreamOwnerRegistry()
    }

    private fun createModule() = VolumeLimitModule(
        limitEnforcer = VolumeLimitEnforcer(volumeTool, normalRingerTool()),
        deviceRepo = deviceRepo,
        ownerRegistry = ownerRegistry,
    )

    private fun device(
        addr: String = address,
        label: String = "Test Device",
        musicVolume: Float? = 0.5f,
        ringVolume: Float? = null,
        volumeLimit: Boolean = true,
        musicVolumeMin: Float? = null,
        musicVolumeMax: Float? = null,
        ringVolumeMax: Float? = null,
    ) = ManagedDevice(
        isConnected = true,
        device = SourceDeviceWrapper(
            address = addr,
            alias = label,
            name = label,
            deviceType = SourceDevice.Type.HEADPHONES,
            isConnected = true,
        ),
        config = DeviceConfigEntity(
            address = addr,
            musicVolume = musicVolume,
            ringVolume = ringVolume,
            volumeLimit = volumeLimit,
            musicVolumeMin = musicVolumeMin,
            musicVolumeMax = musicVolumeMax,
            ringVolumeMax = ringVolumeMax,
            isEnabled = true,
        ),
    )

    private suspend fun seedOwners(vararg devices: ManagedDevice) {
        devicesFlow.value = devices.toList()
        devices.forEachIndexed { index, device ->
            ownerRegistry.onDeviceConnected(
                address = device.address,
                label = device.label,
                deviceType = device.type,
                receivedAtElapsedMs = 1000L,
                sequence = index.toLong(),
            )
        }
    }

    private fun musicEvent(newVolume: Int, self: Boolean = false) =
        VolumeEvent(AudioStream.Id.STREAM_MUSIC, oldVolume = 5, newVolume = newVolume, self = self)

    @Test
    fun `a level inside the band is left alone`() = runTest {
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 5
        seedOwners(device(musicVolumeMin = 0.2f, musicVolumeMax = 0.5f))

        createModule().handle(musicEvent(5))

        verify(exactly = 0) { audioManager.setStreamVolume(any(), any(), any()) }
    }

    @Test
    fun `a level above the maximum is corrected down`() = runTest {
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 15
        seedOwners(device(musicVolumeMax = 0.5f))

        createModule().handle(musicEvent(15))

        audioLevels[AudioStream.Id.STREAM_MUSIC] shouldBe 7
    }

    @Test
    fun `a level below the minimum is corrected up`() = runTest {
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 1
        seedOwners(device(musicVolumeMin = 0.4f))

        createModule().handle(musicEvent(1))

        audioLevels[AudioStream.Id.STREAM_MUSIC] shouldBe 6
    }

    @Test
    fun `self-triggered change is ignored`() = runTest {
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 15
        seedOwners(device(musicVolumeMax = 0.5f))

        createModule().handle(musicEvent(15, self = true))

        verify(exactly = 0) { audioManager.setStreamVolume(any(), any(), any()) }
    }

    @Test
    fun `a non-owning device is not enforced`() = runTest {
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 15
        val owner = device(musicVolume = 0.5f)
        val nonOwner = device(addr = siblingAddress, label = "Other Device", musicVolumeMax = 0.3f)
        devicesFlow.value = listOf(owner, nonOwner)
        ownerRegistry.onDeviceConnected(address, "Test Device", SourceDevice.Type.HEADPHONES, 5000L, 0L)
        ownerRegistry.onDeviceConnected(siblingAddress, "Other Device", SourceDevice.Type.HEADPHONES, 1000L, 1L)

        createModule().handle(musicEvent(15))

        verify(exactly = 0) { audioManager.setStreamVolume(any(), any(), any()) }
    }

    @Test
    fun `an unmanaged stream is not bounded`() = runTest {
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 15
        seedOwners(device(musicVolume = null, musicVolumeMax = 0.5f))

        createModule().handle(musicEvent(15))

        verify(exactly = 0) { audioManager.setStreamVolume(any(), any(), any()) }
    }

    @Test
    fun `the limit toggle being off leaves the level alone`() = runTest {
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 15
        seedOwners(device(volumeLimit = false, musicVolumeMax = 0.5f))

        createModule().handle(musicEvent(15))

        verify(exactly = 0) { audioManager.setStreamVolume(any(), any(), any()) }
    }

    @Test
    fun `a Silent target is left alone`() = runTest {
        audioLevels[AudioStream.Id.STREAM_RINGTONE] = 15
        seedOwners(device(ringVolume = VolumeMode.LEGACY_SILENT_VALUE, ringVolumeMax = 0.5f))

        createModule().handle(VolumeEvent(AudioStream.Id.STREAM_RINGTONE, 5, 15, self = false))

        verify(exactly = 0) { audioManager.setStreamVolume(any(), any(), any()) }
    }

    @Test
    fun `a Vibrate target is left alone`() = runTest {
        audioLevels[AudioStream.Id.STREAM_RINGTONE] = 15
        seedOwners(device(ringVolume = VolumeMode.LEGACY_VIBRATE_VALUE, ringVolumeMax = 0.5f))

        createModule().handle(VolumeEvent(AudioStream.Id.STREAM_RINGTONE, 5, 15, self = false))

        verify(exactly = 0) { audioManager.setStreamVolume(any(), any(), any()) }
    }

    @Test
    fun `a degenerate band resolves to the maximum`() = runTest {
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 15
        seedOwners(device(musicVolumeMin = 0.8f, musicVolumeMax = 0.2f))

        createModule().handle(musicEvent(15))

        audioLevels[AudioStream.Id.STREAM_MUSIC] shouldBe 3
    }

    @Test
    fun `an owner group with disjoint bands produces exactly one write`() = runTest {
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 15
        seedOwners(
            device(musicVolumeMax = 0.5f),
            device(addr = siblingAddress, musicVolumeMax = 0.2f),
        )

        createModule().handle(musicEvent(15))

        verify(exactly = 1) { audioManager.setStreamVolume(AudioStream.Id.STREAM_MUSIC.id, 3, 0) }
        audioLevels[AudioStream.Id.STREAM_MUSIC] shouldBe 3
    }

    private fun toStreamId(id: Int): AudioStream.Id = AudioStream.Id.entries.first { it.id == id }
}
