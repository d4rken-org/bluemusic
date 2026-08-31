package eu.darken.bluemusic.devices.ui.volumelimit

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.SourceDeviceWrapper
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.devices.core.DeviceConfigSaver
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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

    /** The music band of every config the repo was actually told to store, in the order it happened. */
    private val stored = mutableListOf<Pair<Float?, Float?>>()

    private fun TestScope.viewModel(
        infos: MutableStateFlow<UpgradeRepo.Info> = fakeUpgradeInfos(
            FakeUpgradeInfo(isPro = true, isSettled = true)
        ),
        device: ManagedDevice = this@VolumeLimitViewModelTest.device,
        volumeTool: VolumeTool = mockk(relaxed = true),
        gate: CompletableDeferred<Unit>? = null,
    ): VolumeLimitViewModel {
        deviceRepo = mockk<DeviceRepo>(relaxed = true).apply {
            every { devices } returns MutableStateFlow(listOf(device))
            coEvery { isManaged(address) } returns true
            coEvery { updateDevice(any(), any()) } coAnswers {
                gate?.await()
                val update = secondArg<(DeviceConfigEntity) -> DeviceConfigEntity>()
                val updated = update(DeviceConfigEntity(address = firstArg()))
                stored += updated.musicVolumeMin to updated.musicVolumeMax
            }
        }
        navCtrl = mockk(relaxed = true)
        return VolumeLimitViewModel(
            deviceAddress = address,
            deviceRepo = deviceRepo,
            configSaver = DeviceConfigSaver(backgroundScope, deviceRepo),
            upgradeRepo = mockUpgradeRepo(infos),
            volumeTool = volumeTool,
            dispatcherProvider = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
            navCtrl = navCtrl,
        )
    }

    @Test
    fun `a committed band is stored even when the screen is left right away`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val vm = viewModel(gate = gate)

        vm.onLimitChanged(AudioStream.Type.MUSIC, min = 0.2f, max = 0.6f)
        runCurrent()

        // Releasing a thumb and pressing back in the same moment: the screen's scope is gone while the
        // write is still in the database layer.
        vm.vmScope.cancel()
        runCurrent()

        gate.complete(Unit)
        advanceTimeBy(5_000)
        runCurrent()

        stored shouldBe listOf(0.2f to 0.6f)
    }

    // The bounds are checked on the way into the database, and a band that fails the check must not cost
    // the shared write queue the writes that come after it.
    @Test
    fun `an inverted band is dropped without taking later ones with it`() = runTest {
        val vm = viewModel()

        vm.onLimitChanged(AudioStream.Type.MUSIC, min = 0.6f, max = 0.4f)
        advanceTimeBy(5_000)
        runCurrent()

        vm.onLimitChanged(AudioStream.Type.MUSIC, min = 0.2f, max = 0.6f)
        advanceTimeBy(5_000)
        runCurrent()

        stored shouldBe listOf(0.2f to 0.6f)
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
