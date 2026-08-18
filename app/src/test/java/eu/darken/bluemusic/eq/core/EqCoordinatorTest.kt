package eu.darken.bluemusic.eq.core

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import eu.darken.bluemusic.monitor.core.ownership.OwnerSnapshot
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.upgrade.FakeUpgradeInfo
import testhelpers.upgrade.fakeUpgradeInfos
import java.time.Instant

class EqCoordinatorTest : BaseTest() {

    private lateinit var deviceRepo: DeviceRepo
    private lateinit var ownerRegistry: AudioStreamOwnerRegistry
    private lateinit var eligibility: EqEligibility
    private lateinit var tracker: EqSessionTracker
    private lateinit var controller: EqEffectController
    private lateinit var devicesSettings: DevicesSettings
    private lateinit var upgradeRepo: UpgradeRepo

    private lateinit var devicesFlow: MutableStateFlow<List<ManagedDevice>>
    private lateinit var ownerFlow: MutableStateFlow<OwnerSnapshot>
    private lateinit var operationalFlow: MutableStateFlow<Boolean>
    private lateinit var enabledFlow: MutableStateFlow<DevicesSettings.EnabledState>
    private lateinit var trackerFlow: MutableStateFlow<EqSessionState>
    private lateinit var upgradeInfos: MutableStateFlow<UpgradeRepo.Info>

    /** What the fake controller currently has attached, keyed by session id. */
    private lateinit var attached: MutableMap<Int, List<Int>>
    private var attachDelayMs = 0L

    @BeforeEach
    fun setup() {
        devicesFlow = MutableStateFlow(emptyList())
        ownerFlow = MutableStateFlow(OwnerSnapshot())
        operationalFlow = MutableStateFlow(true)
        enabledFlow = MutableStateFlow(DevicesSettings.EnabledState(isEnabled = true, toggleEpoch = 0L))
        trackerFlow = MutableStateFlow(EqSessionState())
        upgradeInfos = fakeUpgradeInfos(FakeUpgradeInfo(isPro = true))
        attached = mutableMapOf()
        attachDelayMs = 0L

        deviceRepo = mockk { every { devices } returns devicesFlow }
        ownerRegistry = mockk { every { ownerSnapshots } returns ownerFlow }
        eligibility = mockk { every { operational } returns operationalFlow }
        devicesSettings = mockk { every { enabledState } returns enabledFlow }
        upgradeRepo = mockk(relaxed = true) { every { upgradeInfo } returns upgradeInfos }

        tracker = mockk(relaxed = true) {
            every { state } returns trackerFlow
            coEvery { startListening() } coAnswers {
                trackerFlow.value = trackerFlow.value.copy(listening = true, generation = trackerFlow.value.generation + 1)
            }
            coEvery { stopListening() } coAnswers {
                trackerFlow.value = trackerFlow.value.copy(listening = false, sessions = emptyMap())
            }
        }

        controller = mockk(relaxed = true) {
            every { attachedSessionIds() } answers { attached.keys.toSet() }
            coEvery { attach(any(), any()) } coAnswers {
                if (attachDelayMs > 0) delay(attachDelayMs)
                attached[firstArg()] = secondArg()
            }
            coEvery { updateLevels(any()) } coAnswers {
                val levels = firstArg<List<Int>>()
                attached.keys.forEach { attached[it] = levels }
            }
            coEvery { detach(any()) } coAnswers { attached.remove(firstArg()) }
            coEvery { detachAll() } coAnswers { attached.clear() }
        }
    }

    private fun createCoordinator(scope: CoroutineScope) = EqCoordinator(
        appScope = scope,
        dispatcherProvider = TestDispatcherProvider(scope.coroutineContext[kotlinx.coroutines.CoroutineDispatcher]),
        deviceRepo = deviceRepo,
        ownerRegistry = ownerRegistry,
        eligibility = eligibility,
        tracker = tracker,
        controller = controller,
        devicesSettings = devicesSettings,
        upgradeRepo = upgradeRepo,
    )

    private fun device(
        address: DeviceAddr,
        eqEnabled: Boolean = true,
        levels: List<Int>? = listOf(300, 0, -300),
        lastConnected: Long = 1_000L,
        connected: Boolean = true,
        isEnabled: Boolean = true,
    ) = ManagedDevice(
        isConnected = connected,
        device = mockk<SourceDevice>(relaxed = true) {
            every { this@mockk.address } returns address
            every { label } returns "Device $address"
            every { deviceType } returns SourceDevice.Type.HEADPHONES
        },
        config = DeviceConfigEntity(
            address = address,
            eqEnabled = eqEnabled,
            eqBandLevels = levels,
            lastConnected = lastConnected,
            isEnabled = isEnabled,
        ),
    )

    private fun openSessions(vararg ids: Int) {
        trackerFlow.value = trackerFlow.value.copy(
            sessions = ids.associateWith { id ->
                EqSession(sessionId = id, generation = trackerFlow.value.generation, openedAt = Instant.EPOCH)
            }
        )
    }

    @Test
    fun `attaches the owner's levels to open sessions`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11, 12)
        runCurrent()

        attached shouldBe mapOf(11 to listOf(300, 0, -300), 12 to listOf(300, 0, -300))
        coVerify { tracker.startListening() }

        coordinator.stopSession(token)
    }

    @Test
    fun `does not attach when the owner has no equalizer enabled`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA", eqEnabled = false))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        openSessions(11)
        runCurrent()

        attached.shouldBeEmpty()
        coVerify(exactly = 0) { controller.attach(any(), any()) }

        coordinator.stopSession(token)
    }

    @Test
    fun `unset levels attach as flat`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA", levels = null))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()

        attached shouldBe mapOf(11 to emptyList())

        coordinator.stopSession(token)
    }

    @Test
    fun `detaches immediately when the owner is lost`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()
        attached.keys shouldBe setOf(11)

        ownerFlow.value = OwnerSnapshot(emptyList(), generation = 2)
        runCurrent()

        attached.shouldBeEmpty()
        coVerify { controller.detachAll() }
        coVerify { tracker.stopListening() }

        coordinator.stopSession(token)
    }

    @Test
    fun `disabling the equalizer for the owner releases everything`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()

        devicesFlow.value = listOf(device("AA", eqEnabled = false))
        runCurrent()

        attached.shouldBeEmpty()

        coordinator.stopSession(token)
    }

    @Test
    fun `the most recently connected config wins inside a grouped owner`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(
            device("LEFT", levels = listOf(100, 100, 100), lastConnected = 1_000L),
            device("RIGHT", levels = listOf(900, 900, 900), lastConnected = 5_000L),
        )
        ownerFlow.value = OwnerSnapshot(listOf("LEFT", "RIGHT"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()

        attached shouldBe mapOf(11 to listOf(900, 900, 900))

        coordinator.stopSession(token)
    }

    @Test
    fun `a new session opening while active is attached automatically`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()

        openSessions(11, 22)
        runCurrent()

        attached.keys shouldBe setOf(11, 22)

        coordinator.stopSession(token)
    }

    @Test
    fun `a closed session is released`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11, 22)
        runCurrent()

        openSessions(22)
        runCurrent()

        attached.keys shouldBe setOf(22)
        coVerify { controller.detach(11) }

        coordinator.stopSession(token)
    }

    @Test
    fun `open then slider preview then owner loss ends detached`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()
        attached shouldBe mapOf(11 to listOf(300, 0, -300))

        coordinator.previewLevels("AA", listOf(1200, 1200, 1200))
        runCurrent()
        attached shouldBe mapOf(11 to listOf(1200, 1200, 1200))
        coVerify { controller.updateLevels(listOf(1200, 1200, 1200)) }

        ownerFlow.value = OwnerSnapshot(emptyList(), generation = 2)
        runCurrent()

        attached.shouldBeEmpty()

        coordinator.stopSession(token)
    }

    @Test
    fun `a preview for another device is ignored`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()

        coordinator.previewLevels("BB", listOf(1200, 1200, 1200))
        runCurrent()

        attached shouldBe mapOf(11 to listOf(300, 0, -300))

        coordinator.stopSession(token)
    }

    @Test
    fun `an attach still in flight when ownership is lost does not survive the release`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        attachDelayMs = 500L
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()

        // Attach is slow: ownership is lost while it is still running.
        openSessions(11)
        runCurrent()
        launch { ownerFlow.value = OwnerSnapshot(emptyList(), generation = 2) }
        advanceTimeBy(2_000)
        runCurrent()

        attached.shouldBeEmpty()

        coordinator.stopSession(token)
    }

    @Test
    fun `stopping the session releases everything`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()

        coordinator.stopSession(token)
        runCurrent()

        attached.shouldBeEmpty()
        coVerify { controller.detachAll() }
        coVerify { tracker.stopListening() }
    }

    @Test
    fun `a recompute after the session was stopped cannot attach again`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()

        coordinator.stopSession(token)

        // Inputs keep changing after the session ended, nothing may re-attach.
        devicesFlow.value = listOf(device("AA", levels = listOf(600, 600, 600)))
        openSessions(11, 22)
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 3)
        runCurrent()

        attached.shouldBeEmpty()
    }

    @Test
    fun `starting a session again replaces the previous one`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val first = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()
        attached.keys shouldBe setOf(11)

        val second = coordinator.startSession()
        runCurrent()
        (second > first) shouldBe true

        // The replaced session released on its way out, the new one attaches again.
        openSessions(11)
        runCurrent()
        attached.keys shouldBe setOf(11)

        coordinator.stopSession(second)
        runCurrent()
        attached.shouldBeEmpty()
    }

    @Test
    fun `stopping with a stale token leaves the running session alone`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val first = coordinator.startSession()
        runCurrent()
        val second = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()
        attached.keys shouldBe setOf(11)

        // The monitor session that owned `first` shuts down late, its stop must not tear this down.
        coordinator.stopSession(first)
        runCurrent()

        attached.keys shouldBe setOf(11)

        coordinator.stopSession(second)
        runCurrent()
        attached.shouldBeEmpty()
    }

    @Test
    fun `a non pro user never starts attaching`() = runTest {
        upgradeInfos.value = FakeUpgradeInfo(isPro = false, isSettled = true)
        operationalFlow.value = false
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        openSessions(11)
        runCurrent()

        attached.shouldBeEmpty()
        coVerify(exactly = 0) { controller.attach(any(), any()) }

        coordinator.stopSession(token)
    }

    @Test
    fun `a disabled app releases everything`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()

        enabledFlow.value = DevicesSettings.EnabledState(isEnabled = false, toggleEpoch = 1L)
        runCurrent()

        attached.shouldBeEmpty()

        coordinator.stopSession(token)
    }
}
