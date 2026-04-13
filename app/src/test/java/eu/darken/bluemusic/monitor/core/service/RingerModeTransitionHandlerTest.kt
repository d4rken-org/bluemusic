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
    private val ownerRegistry = eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry()

    private val handler = RingerModeTransitionHandler(
        deviceRepo = deviceRepo,
        volumeTool = volumeTool,
        observationGate = observationGate,
        ownerRegistry = ownerRegistry,
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

    @Test
    fun `two active devices different names - only owner gets ringer write`() = runTest {
        val ownerAddr = "AA:BB:CC:DD:EE:FF"
        val nonOwnerAddr = "11:22:33:44:55:66"

        val ownerDevice = SourceDeviceWrapper(
            address = ownerAddr,
            alias = "AirPods",
            name = "AirPods",
            deviceType = SourceDevice.Type.HEADPHONES,
            isConnected = true,
        )
        val nonOwnerDevice = SourceDeviceWrapper(
            address = nonOwnerAddr,
            alias = "Speaker",
            name = "Speaker",
            deviceType = SourceDevice.Type.PORTABLE_SPEAKER,
            isConnected = true,
        )

        devicesFlow.value = listOf(
            ManagedDevice(
                isConnected = true,
                device = nonOwnerDevice,
                config = DeviceConfigEntity(
                    address = nonOwnerAddr,
                    ringVolume = 0.5f,
                    notificationVolume = 0.6f,
                    isEnabled = true,
                ),
            ),
            ManagedDevice(
                isConnected = true,
                device = ownerDevice,
                config = DeviceConfigEntity(
                    address = ownerAddr,
                    ringVolume = 0.5f,
                    notificationVolume = 0.6f,
                    isEnabled = true,
                ),
            ),
        )
        // Non-owner connected first, owner connected later → owner wins
        ownerRegistry.onDeviceConnected(nonOwnerAddr, "Speaker", SourceDevice.Type.PORTABLE_SPEAKER, 1000L, 0L)
        ownerRegistry.onDeviceConnected(ownerAddr, "AirPods", SourceDevice.Type.HEADPHONES, 2000L, 1L)

        handler.handle(RingerModeEvent(oldMode = RingerMode.SILENT, newMode = RingerMode.NORMAL))

        coVerify(exactly = 1) { deviceRepo.updateDevice(ownerAddr, any()) }
        coVerify(exactly = 0) { deviceRepo.updateDevice(nonOwnerAddr, any()) }
    }

    @Test
    fun `dual earbuds in owner group - both get ringer write`() = runTest {
        val budL = "AA:BB:CC:DD:EE:01"
        val budR = "AA:BB:CC:DD:EE:02"

        val deviceL = SourceDeviceWrapper(
            address = budL,
            alias = "Buds3 Pro",
            name = "Buds3 Pro",
            deviceType = SourceDevice.Type.HEADPHONES,
            isConnected = true,
        )
        val deviceR = SourceDeviceWrapper(
            address = budR,
            alias = "Buds3 Pro",
            name = "Buds3 Pro",
            deviceType = SourceDevice.Type.HEADPHONES,
            isConnected = true,
        )

        devicesFlow.value = listOf(
            ManagedDevice(
                isConnected = true,
                device = deviceL,
                config = DeviceConfigEntity(
                    address = budL,
                    ringVolume = 0.5f,
                    notificationVolume = 0.6f,
                    isEnabled = true,
                ),
            ),
            ManagedDevice(
                isConnected = true,
                device = deviceR,
                config = DeviceConfigEntity(
                    address = budR,
                    ringVolume = 0.5f,
                    notificationVolume = 0.6f,
                    isEnabled = true,
                ),
            ),
        )
        // Same name + type + within 10s → grouped
        ownerRegistry.onDeviceConnected(budL, "Buds3 Pro", SourceDevice.Type.HEADPHONES, 1000L, 0L)
        ownerRegistry.onDeviceConnected(budR, "Buds3 Pro", SourceDevice.Type.HEADPHONES, 1002L, 1L)

        handler.handle(RingerModeEvent(oldMode = RingerMode.SILENT, newMode = RingerMode.NORMAL))

        coVerify(exactly = 1) { deviceRepo.updateDevice(budL, any()) }
        coVerify(exactly = 1) { deviceRepo.updateDevice(budR, any()) }
    }

    private suspend fun setActiveDevice(
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
        ownerRegistry.onDeviceConnected(
            address = address,
            label = "Test Headphones",
            deviceType = eu.darken.bluemusic.bluetooth.core.SourceDevice.Type.HEADPHONES,
            receivedAtElapsedMs = 1000L,
            sequence = 0L,
        )
    }
}
