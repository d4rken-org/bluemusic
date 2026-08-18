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
import kotlinx.coroutines.channels.Channel
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
    private lateinit var hasEngineFlow: MutableStateFlow<Boolean>
    private lateinit var enabledFlow: MutableStateFlow<DevicesSettings.EnabledState>
    private lateinit var trackerFlow: MutableStateFlow<EqSessionState>
    private lateinit var transitions: Channel<EqTransition>
    private lateinit var upgradeInfos: MutableStateFlow<UpgradeRepo.Info>

    /** What the fake controller currently has attached, keyed by session id. */
    private lateinit var attached: MutableMap<Int, List<Int>>

    /** The boost gain each attached session currently runs with. */
    private lateinit var boosts: MutableMap<Int, Int>
    private var attachDelayMs = 0L
    private var detachAllDelayMs = 0L

    @BeforeEach
    fun setup() {
        devicesFlow = MutableStateFlow(emptyList())
        ownerFlow = MutableStateFlow(OwnerSnapshot())
        hasEngineFlow = MutableStateFlow(true)
        enabledFlow = MutableStateFlow(DevicesSettings.EnabledState(isEnabled = true, toggleEpoch = 0L))
        trackerFlow = MutableStateFlow(EqSessionState())
        transitions = Channel(EqSessionTracker.TRANSITION_BUFFER)
        upgradeInfos = fakeUpgradeInfos(FakeUpgradeInfo(isPro = true))
        attached = mutableMapOf()
        boosts = mutableMapOf()
        attachDelayMs = 0L
        detachAllDelayMs = 0L

        deviceRepo = mockk { every { devices } returns devicesFlow }
        ownerRegistry = mockk { every { ownerSnapshots } returns ownerFlow }
        eligibility = mockk { every { hasEngine } returns hasEngineFlow }
        devicesSettings = mockk { every { enabledState } returns enabledFlow }
        upgradeRepo = mockk(relaxed = true) { every { upgradeInfo } returns upgradeInfos }

        tracker = mockk(relaxed = true) {
            every { state } returns trackerFlow
            // Mirrors the tracker: a stream that was closed is replaced by a fresh one.
            every { transitions() } answers {
                if (this@EqCoordinatorTest.transitions.isClosedForSend) {
                    this@EqCoordinatorTest.transitions = Channel(EqSessionTracker.TRANSITION_BUFFER)
                }
                this@EqCoordinatorTest.transitions
            }
            coEvery { startListening() } coAnswers {
                trackerFlow.value = trackerFlow.value.copy(
                    listening = true,
                    generation = trackerFlow.value.generation + 1,
                    sessions = emptyMap(),
                )
            }
            coEvery { stopListening() } coAnswers {
                trackerFlow.value = trackerFlow.value.copy(
                    listening = false,
                    generation = trackerFlow.value.generation + 1,
                    sessions = emptyMap(),
                )
            }
        }

        controller = mockk(relaxed = true) {
            every { attachedSessionIds() } answers { attached.keys.toSet() }
            coEvery { attach(any(), any(), any()) } coAnswers {
                if (attachDelayMs > 0) delay(attachDelayMs)
                val sessionId = firstArg<Int>()
                // Mirrors the real controller: past the cap an attach is refused, it replaces nothing.
                if (sessionId !in attached && attached.size >= EqEffectController.MAX_ATTACHED) {
                    return@coAnswers
                }
                attached[sessionId] = secondArg()
                boosts[sessionId] = thirdArg()
            }
            coEvery { updateLevels(any()) } coAnswers {
                val levels = firstArg<List<Int>>()
                attached.keys.forEach { attached[it] = levels }
            }
            coEvery { updateBoost(any()) } coAnswers {
                val gain = firstArg<Int>()
                attached.keys.forEach { boosts[it] = gain }
            }
            coEvery { detach(any()) } coAnswers {
                boosts.remove(firstArg())
                attached.remove(firstArg())
            }
            coEvery { detachAll() } coAnswers {
                if (detachAllDelayMs > 0) delay(detachAllDelayMs)
                attached.clear()
                boosts.clear()
            }
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
        boostGain: Int? = null,
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
            eqBoostGain = boostGain,
            lastConnected = lastConnected,
            isEnabled = isEnabled,
        ),
    )

    /** Emits OPEN edges for [ids] on the current listening generation, like a player app would. */
    private fun openSessions(vararg ids: Int) = ids.forEach { id ->
        transitions.trySend(EqTransition(EqTransition.Type.OPEN, id, trackerFlow.value.generation))
        trackerFlow.value = trackerFlow.value.let { state ->
            state.copy(
                sessions = state.sessions + (id to EqSession(id, state.generation, Instant.EPOCH))
            )
        }
    }

    private fun closeSessions(vararg ids: Int) = ids.forEach { id ->
        transitions.trySend(EqTransition(EqTransition.Type.CLOSE, id, trackerFlow.value.generation))
        trackerFlow.value = trackerFlow.value.let { it.copy(sessions = it.sessions - id) }
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
        coVerify(exactly = 0) { controller.attach(any(), any(), any()) }

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

        openSessions(22)
        runCurrent()

        attached.keys shouldBe setOf(11, 22)

        coordinator.stopSession(token)
    }

    // region target address

    @Test
    fun `the device the equalizer runs for is published`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        coordinator.targetAddress.value shouldBe null

        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()

        // Published from the applied target, so it is set before any app opens a session.
        coordinator.targetAddress.value shouldBe "AA"

        openSessions(11)
        runCurrent()
        coordinator.targetAddress.value shouldBe "AA"

        coordinator.stopSession(token)
    }

    @Test
    fun `losing the owner clears the published device`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        coordinator.targetAddress.value shouldBe "AA"

        ownerFlow.value = OwnerSnapshot(emptyList(), generation = 2)
        runCurrent()

        coordinator.targetAddress.value shouldBe null

        coordinator.stopSession(token)
    }

    @Test
    fun `another device taking over is published`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"), device("BB"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        coordinator.targetAddress.value shouldBe "AA"

        ownerFlow.value = OwnerSnapshot(listOf("BB"), generation = 2)
        runCurrent()

        coordinator.targetAddress.value shouldBe "BB"

        coordinator.stopSession(token)
    }

    @Test
    fun `stopping the session clears the published device`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()
        coordinator.targetAddress.value shouldBe "AA"

        coordinator.stopSession(token)
        runCurrent()

        coordinator.targetAddress.value shouldBe null
    }

    @Test
    fun `turning listening off clears the published device`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        coordinator.targetAddress.value shouldBe "AA"

        coordinator.setListening(false)
        runCurrent()

        coordinator.targetAddress.value shouldBe null

        coordinator.stopSession(token)
    }

    // endregion

    // region close grace

    @Test
    fun `a closed session is released once its grace period is over`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11, 22)
        runCurrent()

        closeSessions(11)
        runCurrent()

        // The player may be between tracks, so the effect stays for now.
        attached.keys shouldBe setOf(11, 22)
        coVerify(exactly = 0) { controller.detach(11) }

        advanceTimeBy(2_999)
        runCurrent()
        attached.keys shouldBe setOf(11, 22)

        advanceTimeBy(1)
        runCurrent()

        attached.keys shouldBe setOf(22)
        coVerify(exactly = 1) { controller.detach(11) }

        coordinator.stopSession(token)
    }

    @Test
    fun `a session reopening within the grace period keeps its effect`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()
        attached.keys shouldBe setOf(11)

        // What a track change looks like: the same session id closes and comes right back.
        closeSessions(11)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        openSessions(11)
        runCurrent()

        attached shouldBe mapOf(11 to listOf(300, 0, -300))
        coVerify(exactly = 0) { controller.detach(11) }
        coVerify(exactly = 1) { controller.attach(11, any(), any()) }

        // The grace of the closed edge must not fire behind the reopened session.
        advanceTimeBy(5_000)
        runCurrent()
        attached.keys shouldBe setOf(11)

        coordinator.stopSession(token)
    }

    @Test
    fun `an edit during the grace period reaches the closed session`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA", boostGain = 200))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()

        closeSessions(11)
        runCurrent()

        devicesFlow.value = listOf(device("AA", levels = listOf(600, 600, 600), boostGain = 900))
        runCurrent()

        attached shouldBe mapOf(11 to listOf(600, 600, 600))
        boosts shouldBe mapOf(11 to 900)

        openSessions(11)
        runCurrent()

        attached shouldBe mapOf(11 to listOf(600, 600, 600))
        boosts shouldBe mapOf(11 to 900)
        coVerify(exactly = 1) { controller.attach(11, any(), any()) }

        coordinator.stopSession(token)
    }

    @Test
    fun `a session turned away at the cap is attached once a grace period frees a slot`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        val capped = (1..EqEffectController.MAX_ATTACHED).toList()
        openSessions(*capped.toIntArray())
        runCurrent()
        attached.keys shouldBe capped.toSet()

        closeSessions(1)
        runCurrent()

        val late = EqEffectController.MAX_ATTACHED + 1
        openSessions(late)
        runCurrent()

        // The closing session still holds its slot, so there is nothing left for the new one.
        coVerify(exactly = 1) { controller.attach(late, any(), any()) }
        attached.keys shouldBe capped.toSet()

        advanceTimeBy(3_000)
        runCurrent()

        attached.keys shouldBe (capped - 1 + late).toSet()
        attached[late] shouldBe listOf(300, 0, -300)

        coordinator.stopSession(token)
    }

    @Test
    fun `losing the owner during the grace period releases right away`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()

        closeSessions(11)
        runCurrent()
        attached.keys shouldBe setOf(11)

        ownerFlow.value = OwnerSnapshot(emptyList(), generation = 2)
        runCurrent()

        // No waiting: the device is gone, the grace is only for players juggling sessions.
        attached.shouldBeEmpty()
        coVerify { controller.detachAll() }

        coordinator.stopSession(token)
    }

    @Test
    fun `stopping the session during the grace period releases right away`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()

        closeSessions(11)
        runCurrent()
        attached.keys shouldBe setOf(11)

        coordinator.stopSession(token)
        runCurrent()

        attached.shouldBeEmpty()
        coVerify { controller.detachAll() }
        coVerify { tracker.stopListening() }
    }

    // endregion

    @Test
    fun `a lost edge releases everything and restarts listening`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()
        attached.keys shouldBe setOf(11)
        val generationBefore = trackerFlow.value.generation

        // The tracker gave up on the stream because it could not buffer an edge.
        transitions.close(EqTransitionOverflow("boom"))
        runCurrent()

        attached.shouldBeEmpty()
        trackerFlow.value.listening shouldBe true
        (trackerFlow.value.generation > generationBefore) shouldBe true

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

    // region volume boost

    @Test
    fun `the owner's boost is attached along with the levels`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA", boostGain = 600))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11, 12)
        runCurrent()

        boosts shouldBe mapOf(11 to 600, 12 to 600)
        coVerify { controller.attach(11, listOf(300, 0, -300), 600) }

        coordinator.stopSession(token)
    }

    @Test
    fun `an unset boost attaches as none`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA", boostGain = null))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()

        boosts shouldBe mapOf(11 to 0)

        coordinator.stopSession(token)
    }

    @Test
    fun `changing the stored boost updates the attached sessions`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA", boostGain = 200))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()
        boosts shouldBe mapOf(11 to 200)

        devicesFlow.value = listOf(device("AA", boostGain = 900))
        runCurrent()

        boosts shouldBe mapOf(11 to 900)
        coVerify { controller.updateBoost(900) }
        // The curve did not change, so it must not be rewritten along the way.
        coVerify(exactly = 0) { controller.updateLevels(any()) }

        coordinator.stopSession(token)
    }

    @Test
    fun `a boost preview applies live and is dropped when it is cleared`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA", boostGain = 100))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()
        boosts shouldBe mapOf(11 to 100)

        coordinator.previewBoost("AA", 1000)
        runCurrent()
        boosts shouldBe mapOf(11 to 1000)
        coVerify { controller.updateBoost(1000) }

        coordinator.previewBoost("AA", null)
        runCurrent()
        boosts shouldBe mapOf(11 to 100)

        coordinator.stopSession(token)
    }

    @Test
    fun `a boost preview for another device is ignored`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA", boostGain = 100))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()

        coordinator.previewBoost("BB", 1000)
        runCurrent()

        boosts shouldBe mapOf(11 to 100)

        coordinator.stopSession(token)
    }

    @Test
    fun `previewing levels and boost together keeps both`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA", boostGain = 100))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()

        coordinator.previewBoost("AA", 800)
        runCurrent()
        coordinator.previewLevels("AA", listOf(1200, 1200, 1200))
        runCurrent()

        attached shouldBe mapOf(11 to listOf(1200, 1200, 1200))
        boosts shouldBe mapOf(11 to 800)

        coordinator.stopSession(token)
    }

    @Test
    fun `a session opening during a boost preview attaches with the previewed boost`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA", boostGain = 100))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        coordinator.previewBoost("AA", 700)
        runCurrent()

        openSessions(11)
        runCurrent()

        boosts shouldBe mapOf(11 to 700)

        coordinator.stopSession(token)
    }

    // endregion

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
    fun `a session cannot start while a previous stop is still tearing down`() = runTest {
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val first = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()
        attached.keys shouldBe setOf(11)

        // The teardown of the first session hangs while releasing.
        detachAllDelayMs = 1_000L
        val stopping = launch { coordinator.stopSession(first) }
        advanceTimeBy(100)
        runCurrent()
        stopping.isCompleted shouldBe false

        var second = 0L
        val starting = launch { second = coordinator.startSession() }
        advanceTimeBy(100)
        runCurrent()

        // No new token may exist while the old session is still releasing, or that release would
        // tear down state the new session already built.
        starting.isCompleted shouldBe false
        second shouldBe 0L

        detachAllDelayMs = 0L
        advanceTimeBy(5_000)
        runCurrent()

        stopping.isCompleted shouldBe true
        starting.isCompleted shouldBe true
        (second > first) shouldBe true

        // The old teardown is done, so it cannot release what the new session attaches.
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

    // region entitlement

    @Test
    fun `a non pro user never starts attaching`() = runTest {
        upgradeInfos.value = FakeUpgradeInfo(isPro = false, isSettled = true)
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        // Billing is connected and says no purchase, so the reconciliation denies once it runs out.
        advanceTimeBy(5_000)
        runCurrent()
        openSessions(11)
        runCurrent()

        attached.shouldBeEmpty()
        trackerFlow.value.listening shouldBe false
        coVerify(exactly = 0) { controller.attach(any(), any(), any()) }

        coordinator.stopSession(token)
    }

    @Test
    fun `a device without an equalizer engine never starts attaching`() = runTest {
        hasEngineFlow.value = false
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        runCurrent()
        openSessions(11)
        runCurrent()

        attached.shouldBeEmpty()
        coVerify(exactly = 0) { controller.attach(any(), any(), any()) }

        coordinator.stopSession(token)
    }

    @Test
    fun `billing that never settles does not keep the session idle`() = runTest {
        upgradeInfos.value = FakeUpgradeInfo(isPro = false, isSettled = false)
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        advanceTimeBy(5_000)
        runCurrent()
        openSessions(11)
        runCurrent()

        // Fail-open: an outage must not cost a paying user the feature for the whole session.
        attached shouldBe mapOf(11 to listOf(300, 0, -300))

        coordinator.stopSession(token)
    }

    @Test
    fun `an upgrade during the session activates the equalizer without a restart`() = runTest {
        upgradeInfos.value = FakeUpgradeInfo(isPro = false, isSettled = true)
        val coordinator = createCoordinator(backgroundScope)
        devicesFlow.value = listOf(device("AA"))
        ownerFlow.value = OwnerSnapshot(listOf("AA"), generation = 1)

        val token = coordinator.startSession()
        advanceTimeBy(5_000)
        runCurrent()
        openSessions(11)
        runCurrent()
        attached.shouldBeEmpty()

        upgradeInfos.value = FakeUpgradeInfo(isPro = true, isSettled = true)
        runCurrent()

        trackerFlow.value.listening shouldBe true

        openSessions(11)
        runCurrent()

        attached shouldBe mapOf(11 to listOf(300, 0, -300))

        coordinator.stopSession(token)
    }

    // endregion

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
