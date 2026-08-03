package eu.darken.bluemusic.devices.core

import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.bluetooth.core.speaker.SpeakerDeviceProvider
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

@OptIn(ExperimentalCoroutinesApi::class)
class NewDeviceCreatorTest : BaseTest() {

    private lateinit var deviceRepo: DeviceRepo
    private lateinit var volumeTool: VolumeTool
    private lateinit var bluetoothRepo: BluetoothRepo
    private lateinit var speakerProvider: SpeakerDeviceProvider

    @BeforeEach
    fun setup() {
        deviceRepo = mockk(relaxed = true)
        volumeTool = mockk(relaxed = true)
        bluetoothRepo = mockk(relaxed = true)
        speakerProvider = mockk(relaxed = true)

        every { speakerProvider.address } returns SPEAKER_ADDR
        every { bluetoothRepo.state } returns MutableStateFlow(
            BluetoothRepo.State(isEnabled = false, hasPermission = false, devices = emptySet())
        )
    }

    private fun creator() = NewDeviceCreator(
        deviceRepo = deviceRepo,
        volumeTool = volumeTool,
        bluetoothRepo = bluetoothRepo,
        speakerDeviceProvider = speakerProvider,
    )

    @Test
    fun `speaker device is created without waiting for a ready bluetooth state`() =
        runTest(UnconfinedTestDispatcher()) {
            creator().createNewdevice(SPEAKER_ADDR)

            coVerify { deviceRepo.updateDevice(eq(SPEAKER_ADDR), any()) }
        }

    companion object {
        private const val SPEAKER_ADDR = "self:speaker:main"
    }
}
