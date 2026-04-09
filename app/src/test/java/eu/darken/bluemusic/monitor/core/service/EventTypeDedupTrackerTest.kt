package eu.darken.bluemusic.monitor.core.service

import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.monitor.core.service.BluetoothEventQueue.Event.Type.CONNECTED
import eu.darken.bluemusic.monitor.core.service.BluetoothEventQueue.Event.Type.DISCONNECTED
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.datastore.FakeDataStoreValue
import testhelpers.time.FakeMonotonicClock

class EventTypeDedupTrackerTest : BaseTest() {

    private val budsAddress = "34:E3:FB:94:C2:AF"
    private val speakerAddress = "self:speaker:main"
    private val watchAddress = "11:22:33:44:55:66"

    private lateinit var tracker: EventTypeDedupTracker
    private lateinit var devicesSettings: DevicesSettings
    private lateinit var fakeIsEnabled: FakeDataStoreValue<Boolean>
    private lateinit var clock: FakeMonotonicClock
    private lateinit var trackerScope: CoroutineScope

    @BeforeEach
    fun setup() {
        devicesSettings = mockk()
        fakeIsEnabled = FakeDataStoreValue(initial = true)
        every { devicesSettings.isEnabled } returns fakeIsEnabled.mock

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
        fakeIsEnabled.value = false

        // Toggle back on (simulates user re-enabling)
        fakeIsEnabled.value = true

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
        fakeIsEnabled.value = false

        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false
    }

    /**
     * Reproduces the exact Codex scenario: D → disable → (CONN dropped at
     * receiver) → re-enable → D within TTL must NOT be suppressed.
     */
    @Test
    fun `codex scenario - toggle gap does not suppress later events`() {
        // t=0: first disconnect while enabled
        clock.now = 0
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true

        // t=5s: monitoring disabled
        clock.now = 5_000
        fakeIsEnabled.value = false

        // t=10s: device reconnects. Receiver would early-return on isEnabled=false,
        // so the CONNECTED event never reaches the tracker.
        clock.now = 10_000
        // (no tracker call)

        // t=20s: monitoring re-enabled
        clock.now = 20_000
        fakeIsEnabled.value = true

        // t=25s: device disconnects again. Without the clear-on-toggle fix,
        // isDuplicate would see last=(DISCONNECTED, 0), age=25s < 60s → TRUE
        // and silently drop the real disconnect. With the fix, the map was
        // cleared at t=5s → isDuplicate returns false → event processes.
        clock.now = 25_000
        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
    }

    // --- notifyEnabledState (receiver-driven synchronous clear) ---

    @Test
    fun `notifyEnabledState - true to false clears state synchronously`() {
        clock.now = 1_000
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe true

        // Receiver hands us the new enabled value directly; must clear
        // without waiting for the backstop flow collector.
        tracker.notifyEnabledState(false)

        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false
    }

    @Test
    fun `notifyEnabledState - false to true clears state synchronously`() {
        clock.now = 1_000
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true
        tracker.notifyEnabledState(false)
        // State is gone after the first transition

        // Seed something new while "disabled" (hypothetical path; real
        // receiver would early-return, but the tracker itself is a singleton
        // that can be called from anywhere).
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true

        // Re-enable transition clears again
        tracker.notifyEnabledState(true)
        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false
    }

    @Test
    fun `notifyEnabledState - repeated same value does not clear`() {
        clock.now = 1_000
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true

        // Receiver gets called with isEnabled=true on every broadcast, which is
        // the normal running state — must not clear on every ping.
        tracker.notifyEnabledState(true)
        tracker.notifyEnabledState(true)
        tracker.notifyEnabledState(true)

        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe true
    }

    @Test
    fun `notifyEnabledState - first call matching bootstrap does not clear`() {
        clock.now = 1_000
        tracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true

        // The tracker bootstraps `lastSeenEnabled=true` to match the
        // `devices.enabled` DataStore default. A notifyEnabledState(true) call
        // issued before any real transition must therefore be a no-op — it
        // must NOT falsely clear a map populated by earlier shouldProcess
        // calls.
        tracker.notifyEnabledState(true)

        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe true
    }

    /**
     * Simulates the scheduler race Codex flagged, under the write-site-notify
     * architecture:
     *
     * - The ViewModel write path calls [notifyEnabledState] **synchronously**
     *   alongside the DataStore write (see
     *   [eu.darken.bluemusic.devices.ui.settings.DevicesSettingsViewModel.onToggleEnabled]).
     * - A broadcast arriving between the write and the async flow collector
     *   draining will still see an up-to-date tracker because the write path
     *   already aligned it.
     *
     * Uses a [StandardTestDispatcher] so the tracker's init-block backstop
     * collector stays queued until the test explicitly advances the
     * scheduler. Without the write-site notify, the receiver's observation of
     * `true` would not be able to detect the intermediate `false` and the map
     * would remain stale. With it, the map is correctly cleared before the
     * backstop ever runs.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `race - write-site notify clears before backstop drains`() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val raceFakeEnabled = FakeDataStoreValue(initial = true)
        val raceSettings = mockk<DevicesSettings>().also {
            every { it.isEnabled } returns raceFakeEnabled.mock
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

        // Simulate the ViewModel write path: write to DataStore AND notify
        // the tracker synchronously in the same coroutine. The test scope is
        // NOT advanced between these calls, so the async flow collector
        // queued by the DataStore write has NOT run yet — only the synchronous
        // notify has touched the tracker.
        raceFakeEnabled.value = false
        raceTracker.notifyEnabledState(false)
        raceFakeEnabled.value = true
        raceTracker.notifyEnabledState(true)

        // At this point, the map MUST be cleared purely via the write-site
        // notifications. The backstop collector is still queued in the
        // scheduler and has not drained.
        raceClock.now = 10_000
        raceTracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false
        raceTracker.shouldProcess(budsAddress, DISCONNECTED) shouldBe true

        // Draining the backstop collector afterwards must be idempotent — it
        // may perceive the end-state only (due to StateFlow conflation on
        // rapid writes), and its notifyEnabledState call must not clobber the
        // entry we just legitimately seeded.
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
        val raceFakeEnabled = FakeDataStoreValue(initial = true)
        val raceSettings = mockk<DevicesSettings>().also {
            every { it.isEnabled } returns raceFakeEnabled.mock
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
        raceFakeEnabled.value = false
        testScope.advanceUntilIdle()

        // After observing the transition to `false`, the map is cleared.
        raceClock.now = 10_000
        raceTracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false

        raceFakeEnabled.value = true
        testScope.advanceUntilIdle()

        // And after flipping back, we're still in a clean state for the
        // next broadcast.
        raceClock.now = 11_000
        raceTracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false

        testScope.cancel()
    }
}
