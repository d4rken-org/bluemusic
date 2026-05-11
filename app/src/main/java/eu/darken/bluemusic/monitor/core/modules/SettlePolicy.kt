package eu.darken.bluemusic.monitor.core.modules

import java.time.Duration

/**
 * How a connection module relates to the dispatcher's actionDelay barrier.
 *
 * The dispatcher applies the device's actionDelay **once** between the Immediate group
 * and the rest, instead of every module independently waiting actionDelay at the top of
 * its handler. This collapses the connect pipeline from N*actionDelay to actionDelay +
 * max(work).
 *
 * **Priority semantics:** settle group outranks priority. An [AfterDeviceSettle] module
 * with priority 1 still runs after an [Immediate] module with priority 100.
 */
sealed class SettlePolicy {

    /**
     * Run before the dispatcher sleeps actionDelay. Must be fast (O(ms)) — slow Immediate
     * work delays the start of the settle barrier and effectively extends the wait for
     * every settled module.
     */
    object Immediate : SettlePolicy()

    /**
     * Run after the dispatcher has slept the device's actionDelay once. This is the
     * default for everything that needs the BT audio route stable before acting.
     */
    object AfterDeviceSettle : SettlePolicy()

    /**
     * Run after the dispatcher's actionDelay barrier AND an additional per-module wait.
     * For modules that need more than "BT audio route stable" — e.g. Autoplay needs the
     * launched media app to be foreground-ready to respond to media key events.
     */
    data class AfterDeviceSettlePlus(val extraDelay: Duration) : SettlePolicy()
}
