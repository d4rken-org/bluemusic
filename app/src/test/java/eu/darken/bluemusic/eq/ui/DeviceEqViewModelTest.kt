package eu.darken.bluemusic.eq.ui

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.SourceDeviceWrapper
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.eq.core.EqCapabilities
import eu.darken.bluemusic.eq.core.EqConfigSaver
import eu.darken.bluemusic.eq.core.EqCoordinator
import eu.darken.bluemusic.eq.core.EqPresets
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.upgrade.mockUpgradeRepo

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceEqViewModelTest : BaseTest() {

    private val address = "AA:BB:CC:DD:EE:FF"

    private val caps = EqCapabilities.Caps(
        bandCount = 3,
        minLevel = -1500,
        maxLevel = 1500,
        centerFrequencies = listOf(60_000, 1_000_000, 8_000_000),
    )

    private val device = ManagedDevice(
        isConnected = true,
        device = SourceDeviceWrapper(
            address = address,
            alias = "TestDevice",
            name = "TestDevice",
            deviceType = SourceDevice.Type.HEADPHONES,
            isConnected = true,
        ),
        config = DeviceConfigEntity(address = address, isEnabled = true, eqEnabled = true),
    )

    /** The band curve of every config the repo was actually told to store. */
    private val stored = mutableListOf<List<Int>?>()

    private fun deviceRepo(gate: CompletableDeferred<Unit>): DeviceRepo = mockk<DeviceRepo>(relaxed = true).apply {
        every { devices } returns MutableStateFlow(listOf(device))
        coEvery { updateDevice(any(), any()) } coAnswers {
            gate.await()
            val update = secondArg<(DeviceConfigEntity) -> DeviceConfigEntity>()
            stored += update(DeviceConfigEntity(address = firstArg())).eqBandLevels
        }
    }

    private fun TestScope.viewModel(
        repo: DeviceRepo,
        saver: EqConfigSaver,
        dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler),
    ) = DeviceEqViewModel(
        deviceAddress = address,
        deviceRepo = repo,
        eqCapabilities = mockk<EqCapabilities>(relaxed = true).apply {
            every { capabilities } returns MutableStateFlow(caps)
            coEvery { refreshIfNeeded() } returns caps
        },
        eqPresets = EqPresets(),
        eqCoordinator = mockk<EqCoordinator>(relaxed = true),
        eqConfigSaver = saver,
        upgradeRepo = mockUpgradeRepo(),
        dispatcherProvider = TestDispatcherProvider(dispatcher),
        navCtrl = mockk<NavigationController>(relaxed = true),
    )

    @Test
    fun `a committed curve is stored even when the screen is left right away`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = deviceRepo(gate)
        val vm = viewModel(repo, EqConfigSaver(backgroundScope, repo))

        vm.onLevelsCommitted(listOf(300, 0, -300))
        runCurrent()

        // Releasing the slider and navigating back in the same moment: the screen's scope is gone
        // while the write is still in the database layer.
        vm.vmScope.cancel()
        runCurrent()

        gate.complete(Unit)
        advanceTimeBy(5_000)
        runCurrent()

        stored shouldBe listOf(listOf(300, 0, -300))
    }

    @Test
    fun `a committed preset is stored even when the screen is left right away`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = deviceRepo(gate)
        // A standard dispatcher: nothing the ViewModel launches gets to run eagerly, which is the
        // window a chip tap followed by an immediate back press actually falls into.
        val vm = viewModel(repo, EqConfigSaver(backgroundScope, repo), StandardTestDispatcher(testScheduler))

        backgroundScope.launch { vm.state.collect { } }
        runCurrent()
        vm.state.value!!.presets.map { it.id } shouldBe EqPresets().presets.map { it.id }

        vm.applyPreset(EqPresets.Id.FLAT)

        // The screen dies before any coroutine of the ViewModel had a chance to run.
        vm.vmScope.cancel()
        runCurrent()

        gate.complete(Unit)
        advanceTimeBy(5_000)
        runCurrent()

        stored shouldBe listOf(null)
    }
}
