package eu.darken.bluemusic.devices.ui.volumelimit

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.SourceDeviceWrapper
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.upgrade.FakeUpgradeInfo
import testhelpers.upgrade.fakeUpgradeInfos
import testhelpers.upgrade.mockUpgradeRepo

@OptIn(ExperimentalCoroutinesApi::class)
class VolumeLimitViewModelTest : BaseTest() {

    private val address = "AA:BB:CC:DD:EE:FF"

    private val device = ManagedDevice(
        isConnected = true,
        device = SourceDeviceWrapper(
            address = address,
            alias = "TestDevice",
            name = "TestDevice",
            deviceType = SourceDevice.Type.HEADPHONES,
            isConnected = true,
        ),
        config = DeviceConfigEntity(address = address, isEnabled = true),
    )

    private lateinit var deviceRepo: DeviceRepo
    private lateinit var navCtrl: NavigationController

    private fun TestScope.viewModel(
        infos: MutableStateFlow<UpgradeRepo.Info> = fakeUpgradeInfos(
            FakeUpgradeInfo(isPro = true, isSettled = true)
        ),
        device: ManagedDevice = this@VolumeLimitViewModelTest.device,
        volumeTool: VolumeTool = mockk(relaxed = true),
    ): VolumeLimitViewModel {
        deviceRepo = mockk<DeviceRepo>(relaxed = true).apply {
            every { devices } returns MutableStateFlow(listOf(device))
            coEvery { isManaged(address) } returns true
        }
        navCtrl = mockk(relaxed = true)
        return VolumeLimitViewModel(
            deviceAddress = address,
            deviceRepo = deviceRepo,
            upgradeRepo = mockUpgradeRepo(infos),
            volumeTool = volumeTool,
            dispatcherProvider = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
            navCtrl = navCtrl,
        )
    }

    // The limit slider snaps to hardware levels, so it needs each managed stream's step count.
    @Test
    fun `the step counts cover managed streams`() = runTest {
        val volumeTool = mockk<VolumeTool>(relaxed = true).apply {
            every { getMinVolume(AudioStream.Id.STREAM_MUSIC) } returns 0
            every { getMaxVolume(AudioStream.Id.STREAM_MUSIC) } returns 15
            // A stream without travel offers nothing to pick between.
            every { getMinVolume(AudioStream.Id.STREAM_ALARM) } returns 7
            every { getMaxVolume(AudioStream.Id.STREAM_ALARM) } returns 7
        }
        val vm = viewModel(
            device = device.copy(
                config = device.config.copy(musicVolume = 0.5f, alarmVolume = 0.5f)
            ),
            volumeTool = volumeTool,
        )

        vm.state.filterNotNull().first().volumeStepCounts shouldBe mapOf(AudioStream.Type.MUSIC to 15)
    }

    @Test
    fun `an unmeasurable stream is left out of the step counts`() = runTest {
        val volumeTool = mockk<VolumeTool>(relaxed = true).apply {
            every {
                getMaxVolume(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE)
            } throws IllegalArgumentException("no such stream")
        }
        val vm = viewModel(
            device = device.copy(config = device.config.copy(callVolume = 0.5f)),
            volumeTool = volumeTool,
        )

        vm.state.filterNotNull().first().volumeStepCounts shouldBe emptyMap()
    }
}
