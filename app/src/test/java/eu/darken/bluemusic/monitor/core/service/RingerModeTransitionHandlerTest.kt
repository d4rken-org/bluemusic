package eu.darken.bluemusic.monitor.core.service

import android.media.AudioManager
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.SourceDeviceWrapper
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.RingerMode
import eu.darken.bluemusic.monitor.core.audio.RingerModeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.volume.VolumeObservationGate
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class RingerModeTransitionHandlerTest : BaseTest() {

    private val address = "AA:BB:CC:DD:EE:FF"
    private val device = SourceDeviceWrapper(
        address = address,
        alias = "Test Headphones",
        name = "Test Headphones",
        deviceType = SourceDevice.Type.HEADPHONES,
        isConnected = true,
    )

    private val devicesFlow = MutableStateFlow(emptyList<ManagedDevice>())
    private val deviceRepo = mockk<DeviceRepo>(relaxed = true)
    private val volumeTool = mockk<VolumeTool>(relaxed = true)
    private val observationGate = VolumeObservationGate()

    private val handler = RingerModeTransitionHandler(
        deviceRepo = deviceRepo,
        volumeTool = volumeTool,
        observationGate = observationGate,
    )

    @BeforeEach
    fun setup() {
        every { deviceRepo.devices } returns devicesFlow
    }

    @Test
    fun `transition to NORMAL suppresses notification and re-applies stored volume`() = runTest {
        setActiveDevice(notificationVolume = 0.6f)

        handler.handle(RingerModeEvent(oldMode = RingerMode.SILENT, newMode = RingerMode.NORMAL))

        coVerify(exactly = 1) {
            volumeTool.changeVolume(
                streamId = AudioStream.Id.STREAM_NOTIFICATION,
                percent = 0.6f,
                visible = false,
            )
        }
    }

    @Test
    fun `notification is unsuppressed after transition to NORMAL completes`() = runTest {
        setActiveDevice(notificationVolume = 0.6f)

        handler.handle(RingerModeEvent(oldMode = RingerMode.SILENT, newMode = RingerMode.NORMAL))

        observationGate.isSuppressed(AudioStream.Id.STREAM_NOTIFICATION) shouldBe false
    }

    @Test
    fun `transition to NORMAL without stored notification volume skips suppression`() = runTest {
        setActiveDevice(notificationVolume = null)

        handler.handle(RingerModeEvent(oldMode = RingerMode.SILENT, newMode = RingerMode.NORMAL))

        coVerify(exactly = 0) { volumeTool.changeVolume(any<AudioStream.Id>(), any<Float>(), any<Boolean>()) }
        observationGate.isSuppressed(AudioStream.Id.STREAM_NOTIFICATION) shouldBe false
    }

    @Test
    fun `transition to SILENT does not suppress notification`() = runTest {
        setActiveDevice(notificationVolume = 0.6f)

        handler.handle(RingerModeEvent(oldMode = RingerMode.NORMAL, newMode = RingerMode.SILENT))

        coVerify(exactly = 0) { volumeTool.changeVolume(any<AudioStream.Id>(), any<Float>(), any<Boolean>()) }
        observationGate.isSuppressed(AudioStream.Id.STREAM_NOTIFICATION) shouldBe false
    }

    @Test
    fun `transition to VIBRATE does not suppress notification`() = runTest {
        setActiveDevice(notificationVolume = 0.6f)

        handler.handle(RingerModeEvent(oldMode = RingerMode.NORMAL, newMode = RingerMode.VIBRATE))

        coVerify(exactly = 0) { volumeTool.changeVolume(any<AudioStream.Id>(), any<Float>(), any<Boolean>()) }
        observationGate.isSuppressed(AudioStream.Id.STREAM_NOTIFICATION) shouldBe false
    }

    @Test
    fun `no active device is a no-op`() = runTest {
        devicesFlow.value = emptyList()

        handler.handle(RingerModeEvent(oldMode = RingerMode.SILENT, newMode = RingerMode.NORMAL))

        coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
    }

    @Test
    fun `device without ringtone volume configured is a no-op`() = runTest {
        setActiveDevice(ringVolume = null, notificationVolume = 0.6f)

        handler.handle(RingerModeEvent(oldMode = RingerMode.SILENT, newMode = RingerMode.NORMAL))

        coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
    }

    @Test
    fun `notification suppression is released even when updateDevice throws`() = runTest {
        setActiveDevice(notificationVolume = 0.6f)
        coEvery { deviceRepo.updateDevice(any(), any()) } throws RuntimeException("DB error")

        try {
            handler.handle(RingerModeEvent(oldMode = RingerMode.SILENT, newMode = RingerMode.NORMAL))
        } catch (_: RuntimeException) {
            // expected
        }

        observationGate.isSuppressed(AudioStream.Id.STREAM_NOTIFICATION) shouldBe false
    }

    private fun setActiveDevice(
        ringVolume: Float? = 0.5f,
        notificationVolume: Float? = null,
    ) {
        devicesFlow.value = listOf(
            ManagedDevice(
                isConnected = true,
                device = device,
                config = DeviceConfigEntity(
                    address = address,
                    lastConnected = 0L,
                    ringVolume = ringVolume,
                    notificationVolume = notificationVolume,
                    isEnabled = true,
                ),
            )
        )
    }
}
