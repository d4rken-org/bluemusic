package eu.darken.bluemusic.monitor.core.service

import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.monitor.core.service.BluetoothEventQueue.Event.Type.CONNECTED
import eu.darken.bluemusic.monitor.core.service.BluetoothEventQueue.Event.Type.DISCONNECTED
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.time.FakeMonotonicClock

class EventTypeDedupTrackerTest : BaseTest() {

    private val budsAddress = "34:E3:FB:94:C2:AF"
    private val speakerAddress = "self:speaker:main"
    private val watchAddress = "11:22:33:44:55:66"

    private lateinit var tracker: EventTypeDedupTracker
    private lateinit var devicesSettings: DevicesSettings
    private lateinit var enabledStateFlow: MutableStateFlow<DevicesSettings.EnabledState>
    private lateinit var currentEnabledState: DevicesSettings.EnabledState
    private lateinit var clock: FakeMonotonicClock
    private lateinit var trackerScope: CoroutineScope

    @BeforeEach
    fun setup() {
        devicesSettings = mockk()
        currentEnabledState = DevicesSettings.EnabledState(isEnabled = true, toggleEpoch = 0L)
        enabledStateFlow = MutableStateFlow(currentEnabledState)
        every { devicesSettings.enabledState } returns enabledStateFlow
        coEvery { devicesSettings.currentEnabledState() } answers { currentEnabledState }

        clock = FakeMonotonicClock(now = 0L)
        trackerScope = CoroutineScope(Dispatchers.Unconfined + Job())

        tracker = EventTypeDedupTracker(
            appScope = trackerScope,
            devicesSettings = devicesSettings,
            clock = clock,
        )
    }

    @AfterEach
    fun teardown() {
        trackerScope.cancel()
    }

    private fun setEnabled(enabled: Boolean) {
        currentEnabledState = if (currentEnabledState.isEnabled == enabled) {
            currentEnabledState
        } else {
            currentEnabledState.copy(isEnabled = enabled, toggleEpoch = currentEnabledState.toggleEpoch + 1L)
        }
        enabledStateFlow.value = currentEnabledState
    }

    // --- shouldProcess basics ---

    @Test
    fun `first event for fresh device is processed`() {
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
    }

    @Test
    fun `duplicate event with same type is skipped`() {
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe false
    }

    @Test
    fun `real transition D1 C1 D2 all process`() {
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
        tracker.shouldProcess(budsAddress, CONNECTED) shouldBe true
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
    }

    /**
     * Captures the exact sequence from scott's log
     * (bluemusic_3.2.4-rc0_20260408T123558Z): buds disconnect, speaker takes
     * over, then a stale buds DISCONNECTED arrives ~10s later.
     */
    @Test
    fun `user's actual bug - stale buds disconnect skipped`() {
        clock.now = 100_000
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
        clock.now = 103_000
        tracker.shouldProcess(speakerAddress, CONNECTED) shouldBe true
        // Buds ACL_DISCONNECTED #2 (Samsung duplicate, ~10s later)
        clock.now = 110_000
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe false
    }

    @Test
    fun `different devices are independent`() {
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
        tracker.shouldProcess(watchAddress, DISCONNECTED) shouldBe true
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe false
        tracker.shouldProcess(watchAddress, DISCONNECTED) shouldBe false
    }

    @Test
    fun `same type after opposite type is processed`() {
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
        tracker.shouldProcess(budsAddress, CONNECTED) shouldBe true
        tracker.shouldProcess(budsAddress, CONNECTED) shouldBe false
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
    }

    @Test
    fun `duplicate within TTL window is skipped`() {
        clock.now = 1_000
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
        // Within TTL
        clock.now = EventTypeDedupTracker.TTL_MS - 1
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe false
    }

    @Test
    fun `same type at TTL boundary is processed`() {
        clock.now = 0
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
        // Exactly at TTL boundary — ageMs == TTL_MS, NOT < TTL_MS → processed
        clock.now = EventTypeDedupTracker.TTL_MS
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
    }

    @Test
    fun `same type well past TTL is processed`() {
        clock.now = 0
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
        clock.now = EventTypeDedupTracker.TTL_MS * 3
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
    }

    /**
     * Recovery from a dropped upstream event: if a CONNECTED event was lost
     * somewhere between two DISCONNECTED events, the second DISCONNECTED would
     * be suppressed by pure type-only dedup. With TTL, once enough time has
     * passed, the second DISCONNECTED processes again.
     */
    @Test
    fun `recovers from dropped intermediate event after TTL`() {
        clock.now = 0
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
        // (upstream CONNECTED event was dropped — never reached the tracker)
        clock.now = EventTypeDedupTracker.TTL_MS + 1_000
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
    }

    /**
     * Symmetric missed-ACL recovery for the `C → [missed D] → C` direction.
     * Documents the rationale for keeping [EventTypeDedupTracker.TTL_MS]
     * narrow: once the window has elapsed, a legitimate reconnect after a
     * missed intermediate DISCONNECTED must process so the user's audio
     * route and volume restore actually run.
     */
    @Test
    fun `recovers from dropped intermediate disconnect after TTL`() {
        clock.now = 0
        tracker.shouldProcess(budsAddress, CONNECTED) shouldBe true
        // Intermediate DISCONNECTED was missed (doze, system load, OEM kill).
        clock.now = EventTypeDedupTracker.TTL_MS + 100
        tracker.shouldProcess(budsAddress, CONNECTED) shouldBe true
    }

    @Test
    fun `TTL is per device, not global`() {
        clock.now = 0
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
        clock.now = 100
        tracker.shouldProcess(watchAddress, DISCONNECTED) shouldBe true
        clock.now = 5_000
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe false
        tracker.shouldProcess(watchAddress, DISCONNECTED) shouldBe false
    }

    // --- isDuplicate (read-only pre-filter) ---

    @Test
    fun `isDuplicate returns false for fresh device`() {
        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false
    }

    @Test
    fun `isDuplicate returns true for same type within TTL`() {
        clock.now = 1_000
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
        clock.now = 10_000
        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe true
    }

    @Test
    fun `isDuplicate returns false for same type past TTL`() {
        clock.now = 0
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
        clock.now = EventTypeDedupTracker.TTL_MS
        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false
        clock.now = EventTypeDedupTracker.TTL_MS + 1
        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false
    }

    @Test
    fun `isDuplicate returns false for different type`() {
        clock.now = 0
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
        clock.now = 100
        tracker.isDuplicate(budsAddress, CONNECTED) shouldBe false
    }

    @Test
    fun `isDuplicate does not mutate state`() {
        repeat(5) { tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false }
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe true
    }

    /**
     * The critical invariant that fixes the C1 double-dedup bug: calling
     * isDuplicate before shouldProcess on the same (address, type) must still
     * allow shouldProcess to accept the event as a first occurrence.
     */
    @Test
    fun `isDuplicate before shouldProcess does not poison first-occurrence event`() {
        clock.now = 1_000
        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false
        clock.now = 1_001
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
        clock.now = 11_000
        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe true
    }

    @Test
    fun `isDuplicate is per device`() {
        clock.now = 0
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
        clock.now = 100
        tracker.isDuplicate(watchAddress, DISCONNECTED) shouldBe false
        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe true
    }

    // --- Eviction (W2) ---

    @Test
    fun `shouldProcess evicts entries older than eviction age`() {
        clock.now = 0
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
        tracker.shouldProcess(watchAddress, CONNECTED) shouldBe true
        tracker.shouldProcess(speakerAddress, DISCONNECTED) shouldBe true

        // Trigger a put well past the eviction age
        clock.now = EventTypeDedupTracker.EVICTION_AGE_MS + 1_000
        tracker.shouldProcess("aa:bb:cc:dd:ee:ff", CONNECTED) shouldBe true

        // Original entries should be evicted — isDuplicate returns false for them
        clock.now = EventTypeDedupTracker.EVICTION_AGE_MS + 1_100
        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false
        tracker.isDuplicate(watchAddress, CONNECTED) shouldBe false
        tracker.isDuplicate(speakerAddress, DISCONNECTED) shouldBe false
    }

    // --- isEnabled reset (Codex #2) ---

    /**
     * Regression test for the Codex finding: if monitoring is disabled while a
     * real device state transition happens (receiver early-returns before
     * reaching the tracker), the singleton tracker still holds the pre-disable
     * state. When re-enabled, the next real event within TTL would be
     * incorrectly suppressed. Clearing the tracker on any `isEnabled` toggle
     * prevents this.
     */
    @Test
    fun `clears dedup state on isEnabled toggle`() {
        // Seed state while enabled
        clock.now = 1_000
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe true

        // Toggle off (simulates user disabling monitoring)
        setEnabled(false)

        // Toggle back on (simulates user re-enabling)
        setEnabled(true)

        // Map should be cleared — the same event should no longer be a duplicate
        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
    }

    @Test
    fun `clears dedup state on disabled transition even without re-enable`() {
        clock.now = 1_000
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true

        // Transition to disabled — the state is cleared as soon as we detect
        // the change, so re-enabling after an arbitrary delay still starts fresh.
        setEnabled(false)

        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false
    }

    /**
     * Reproduces the exact Codex scenario: D → disable → (CONN dropped at
     * receiver) → re-enable → D within TTL must NOT be suppressed. Timeline
     * is compressed to fit inside the 15s TTL so the test still exercises the
     * "second D would be suppressed by TTL without the clear-on-toggle fix"
     * path.
     */
    @Test
    fun `codex scenario - toggle gap does not suppress later events`() {
        // t=0: first disconnect while enabled
        clock.now = 0
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true

        // t=2s: monitoring disabled
        clock.now = 2_000
        setEnabled(false)

        // t=5s: device reconnects. Receiver would early-return on isEnabled=false,
        // so the CONNECTED event never reaches the tracker.
        clock.now = 5_000
        // (no tracker call)

        // t=8s: monitoring re-enabled
        clock.now = 8_000
        setEnabled(true)

        // t=12s: device disconnects again. Without the clear-on-toggle fix,
        // isDuplicate would see last=(DISCONNECTED, 0), age=12s < 15s → TRUE
        // and silently drop the real disconnect. With the fix, the map was
        // cleared at t=2s → isDuplicate returns false → event processes.
        clock.now = 12_000
        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
    }

    // --- observeEnabledState (receiver/dispatcher-driven synchronous clear) ---

    @Test
    fun `observeEnabledState - toggle to disabled clears state synchronously`() {
        clock.now = 1_000
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe true

        tracker.observeEnabledState(
            DevicesSettings.EnabledState(isEnabled = false, toggleEpoch = 1L)
        )

        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false
    }

    @Test
    fun `observeEnabledState - epoch change clears even when boolean is true`() {
        clock.now = 1_000
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true

        // Collapsed true -> false -> true cycle: the observer only sees the final
        // `true`, but the epoch proves a toggle happened and must still clear.
        tracker.observeEnabledState(
            DevicesSettings.EnabledState(isEnabled = true, toggleEpoch = 2L)
        )
        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false
    }

    @Test
    fun `observeEnabledState - repeated same epoch does not clear`() {
        clock.now = 1_000
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true

        tracker.observeEnabledState(DevicesSettings.EnabledState(isEnabled = true, toggleEpoch = 0L))
        tracker.observeEnabledState(DevicesSettings.EnabledState(isEnabled = true, toggleEpoch = 0L))
        tracker.observeEnabledState(DevicesSettings.EnabledState(isEnabled = true, toggleEpoch = 0L))

        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe true
    }

    @Test
    fun `observeEnabledState - first call matching bootstrap does not clear`() {
        clock.now = 1_000
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true

        tracker.observeEnabledState(DevicesSettings.EnabledState(isEnabled = true, toggleEpoch = 0L))

        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe true
    }

    /**
     * Simulates the collapsed-toggle race:
     *
     * - DataStore commits `enabled=false, epoch=1`, then `enabled=true, epoch=2`
     *   before the backstop collector drains.
     * - The receiver/dispatcher only observes the final `enabled=true`, but its
     *   atomic snapshot still carries `epoch=2`.
     * - Observing that snapshot must clear the map immediately, before the
     *   queued flow collector runs.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `race - observed epoch clears before backstop drains`() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        var raceState = DevicesSettings.EnabledState(isEnabled = true, toggleEpoch = 0L)
        val raceStateFlow = MutableStateFlow(raceState)
        val raceSettings = mockk<DevicesSettings>().also {
            every { it.enabledState } returns raceStateFlow
            coEvery { it.currentEnabledState() } answers { raceState }
        }
        val raceClock = FakeMonotonicClock(now = 0L)
        val raceTracker = EventTypeDedupTracker(
            appScope = testScope,
            devicesSettings = raceSettings,
            clock = raceClock,
        )

        // Drain the tracker's init subscription to its initial state so the
        // drop(1) pipeline is ready (but no real emissions yet).
        testScope.advanceUntilIdle()

        // Seed state with an entry that would be "stale" after a toggle.
        raceClock.now = 1_000
        raceTracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true

        // Two writes happen before the backstop collector drains. StateFlow may
        // conflate them to the final `true`, but the epoch still reflects both
        // toggles.
        raceState = DevicesSettings.EnabledState(isEnabled = false, toggleEpoch = 1L)
        raceStateFlow.value = raceState
        raceState = DevicesSettings.EnabledState(isEnabled = true, toggleEpoch = 2L)
        raceStateFlow.value = raceState

        // The receiver/dispatcher path sees only the final `true`, but the
        // epoch proves a toggle happened and must clear synchronously.
        raceTracker.observeEnabledState(raceState)
        raceClock.now = 10_000
        raceTracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false
        raceTracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true

        // Draining the backstop collector afterwards must be idempotent.
        testScope.advanceUntilIdle()

        raceClock.now = 11_000
        raceTracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe true

        testScope.cancel()
    }

    /**
     * Verifies the async backstop collector clears the map when it gets a
     * chance to observe a transition. This guards against accidentally making
     * the backstop a pure no-op (which would cripple the path in scenarios
     * where no broadcast coincides with the toggle).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `backstop collector clears state on observed transition`() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        var raceState = DevicesSettings.EnabledState(isEnabled = true, toggleEpoch = 0L)
        val raceStateFlow = MutableStateFlow(raceState)
        val raceSettings = mockk<DevicesSettings>().also {
            every { it.enabledState } returns raceStateFlow
            coEvery { it.currentEnabledState() } answers { raceState }
        }
        val raceClock = FakeMonotonicClock(now = 0L)
        val raceTracker = EventTypeDedupTracker(
            appScope = testScope,
            devicesSettings = raceSettings,
            clock = raceClock,
        )
        testScope.advanceUntilIdle()

        raceClock.now = 1_000
        raceTracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true

        // Toggle and drain between each write so the collector actually sees
        // both emissions (StateFlow conflation would otherwise collapse rapid
        // writes to the end state only).
        raceState = DevicesSettings.EnabledState(isEnabled = false, toggleEpoch = 1L)
        raceStateFlow.value = raceState
        testScope.advanceUntilIdle()

        // After observing the transition to `false`, the map is cleared.
        raceClock.now = 10_000
        raceTracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false

        raceState = DevicesSettings.EnabledState(isEnabled = true, toggleEpoch = 2L)
        raceStateFlow.value = raceState
        testScope.advanceUntilIdle()

        // And after flipping back, we're still in a clean state for the
        // next broadcast.
        raceClock.now = 11_000
        raceTracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false

        testScope.cancel()
    }
}
