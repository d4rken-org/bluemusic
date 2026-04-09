package eu.darken.bluemusic.monitor.core.service

import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.time.MonotonicClock
import eu.darken.bluemusic.devices.core.DevicesSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-device dedup for [BluetoothEventQueue.Event.Type].
 *
 * Some devices (e.g. Samsung Galaxy Buds 3 Pro) emit a duplicate ACL_DISCONNECTED
 * broadcast many seconds after the first one. If we run the disconnect pipeline
 * again, [eu.darken.bluemusic.monitor.core.modules.volume.VolumeDisconnectModule]
 * captures the *current* (possibly mid-ramp) system volumes and overwrites the
 * user's previously-saved values.
 *
 * This tracker is consulted at two layers sharing one [@Singleton] instance:
 *
 * - [eu.darken.bluemusic.monitor.core.receiver.MonitorEventReceiver] calls
 *   [isDuplicate] as a **read-only pre-filter** to skip wasted broadcast work
 *   (event building, debouncer reschedule, 10s goAsync delay) when the
 *   broadcast is an obvious duplicate.
 * - [EventDispatcher] calls [shouldProcess] as the **authoritative commit** —
 *   it is the single place that mutates the map. Only events that pass this
 *   check reach [eu.darken.bluemusic.monitor.core.modules.ConnectionModule]s.
 *
 * The split avoids a "double-dedup" bug where both layers mutating state for
 * the same event would cause the second caller (the dispatcher) to see its own
 * first-occurrence event as a duplicate.
 *
 * Uses a [MonotonicClock] so the dedup window is unaffected by wall-clock
 * changes (NTP resync, user time adjustment). Also auto-resets on any
 * [DevicesSettings.isEnabled] toggle, because the receiver short-circuits
 * events while monitoring is disabled — so tracker state from before a
 * disabled gap cannot be trusted to reflect reality after re-enable.
 *
 * Reset detection is driven by a monotonic toggle epoch in
 * [DevicesSettings.EnabledState]. The epoch advances on every actual toggle,
 * even if a consumer only observes the final boolean value after a rapid
 * `true -> false -> true` cycle.
 *
 * Observing that epoch is driven by two paths:
 *
 * 1. [MonitorEventReceiver] and [EventDispatcher] call [observeEnabledState]
 *    from the same snapshot they use for event handling, so queue consumers
 *    can synchronously clear stale dedup state before consulting
 *    [isDuplicate]/[shouldProcess].
 * 2. The [DevicesSettings.enabledState] collector in [init] handles toggles
 *    that happen while no event is being processed.
 *
 * Thread-safe via [@Synchronized]. Expected to be called both from the
 * sequential [BluetoothEventQueue.events] consumer in `MonitorService` AND from
 * multiple `MonitorEventReceiver` coroutines running in parallel on
 * `Dispatchers.Default`.
 */
@Singleton
class EventTypeDedupTracker @Inject constructor(
    @AppScope appScope: CoroutineScope,
    devicesSettings: DevicesSettings,
    private val clock: MonotonicClock,
) {

    private val lastProcessedEventType =
        mutableMapOf<String, Pair<BluetoothEventQueue.Event.Type, Long>>()

    private var lastSeenEnabledState = DevicesSettings.EnabledState(
        isEnabled = true,
        toggleEpoch = 0L,
    )

    init {
        devicesSettings.enabledState
            .drop(1) // ignore initial replay
            .distinctUntilChanged()
            .onEach { observeEnabledState(it) }
            .launchIn(appScope)
    }

    /**
     * Synchronously observes the caller's atomic enabled snapshot. A changed epoch means at least
     * one toggle happened since the last observation, so stale dedup state must be cleared even if
     * the current boolean matches the earlier one (for example a collapsed
     * `true -> false -> true` cycle).
     */
    @Synchronized
    fun observeEnabledState(currentState: DevicesSettings.EnabledState) {
        val previousState = lastSeenEnabledState
        lastSeenEnabledState = currentState
        if (previousState.toggleEpoch != currentState.toggleEpoch) {
            log(TAG, INFO) {
                "Monitoring epoch advanced " +
                    "(${previousState.toggleEpoch} → ${currentState.toggleEpoch}, " +
                    "enabled=${previousState.isEnabled} → ${currentState.isEnabled}), clearing dedup state"
            }
            lastProcessedEventType.clear()
        }
    }

    /**
     * Read-only: returns `true` if an event with `(address, type)` would be
     * treated as a duplicate by [shouldProcess] at this instant, `false`
     * otherwise. Does **not** mutate internal state.
     *
     * Use this for pre-filter optimizations where the authoritative commit
     * (state mutation) happens in another call site. Calling [isDuplicate]
     * from any call site is always safe with respect to a later [shouldProcess]
     * call on a different call site — the commit still runs correctly.
     */
    @Synchronized
    fun isDuplicate(
        address: String,
        type: BluetoothEventQueue.Event.Type,
    ): Boolean {
        val last = lastProcessedEventType[address] ?: return false
        if (last.first != type) return false
        return (clock.nowMs() - last.second) < TTL_MS
    }

    /**
     * Read-write: returns `true` if the event should be processed, `false` if
     * it is a duplicate of the last processed event for the same device
     * address within [TTL_MS].
     *
     * When the event is accepted, the internal state is updated so that a
     * subsequent call with the same `(address, type)` within the TTL returns
     * `false`. Opportunistically evicts entries older than [EVICTION_AGE_MS]
     * to keep the map bounded.
     */
    @Synchronized
    fun shouldProcess(
        address: String,
        type: BluetoothEventQueue.Event.Type,
    ): Boolean {
        val now = clock.nowMs()
        val last = lastProcessedEventType[address]
        if (last != null && last.first == type) {
            val ageMs = now - last.second
            if (ageMs < TTL_MS) {
                log(TAG, INFO) { "Ignoring duplicate $type for $address (last seen ${ageMs}ms ago)" }
                return false
            }
            log(TAG, INFO) { "Accepting same-type $type for $address after TTL (${ageMs}ms ago)" }
        }
        evictStaleEntries(now)
        lastProcessedEventType[address] = type to now
        return true
    }

    /**
     * Clears all dedup state. Called automatically on [DevicesSettings.isEnabled]
     * transitions, and exposed for tests.
     */
    @Synchronized
    fun clear() {
        lastProcessedEventType.clear()
    }

    private fun evictStaleEntries(now: Long) {
        lastProcessedEventType.entries.removeAll { (_, value) ->
            (now - value.second) > EVICTION_AGE_MS
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "Event", "Dedup")

        /**
         * Events of the same type for the same device within this window are
         * treated as duplicates and dropped. 60s gives comfortable headroom
         * over scott's observed 10s Samsung duplicates while staying well
         * under any plausible legit same-type repeat (which would require an
         * intervening opposite-type event to make sense physically).
         */
        const val TTL_MS: Long = 60_000L

        /**
         * Entries older than this age are evicted during [shouldProcess] to
         * bound map size over long-running sessions. Set to 2x [TTL_MS] so
         * entries are only dropped well after they stopped being relevant to
         * the dedup decision.
         */
        const val EVICTION_AGE_MS: Long = 2 * TTL_MS
    }
}
