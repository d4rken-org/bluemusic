package eu.darken.bluemusic.upgrade.core.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.setupCommonEventHandlers
import eu.darken.bluemusic.upgrade.core.billing.client.BillingClientException
import eu.darken.bluemusic.upgrade.core.billing.client.BillingConnection
import eu.darken.bluemusic.upgrade.core.billing.client.BillingConnectionProvider
import eu.darken.bluemusic.upgrade.core.billing.client.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    connectionProvider: BillingConnectionProvider,
) {

    // Fresh Play data plus its provenance: a query result covers all owned products, while a
    // purchase event only carries the products of that transaction — consumers deciding between
    // per-SKU behaviors (like grace windows) need to know the difference.
    data class FreshData(
        val data: BillingData,
        val isFullSnapshot: Boolean,
    )

    // Emits only data that was *freshly* obtained from Play: per-connection/manual query results and
    // completed purchase events. Unlike billingData below (whose shareIn replay re-serves old data to
    // late subscribers), every emission here represents an actual Play round-trip, so consumers can
    // safely use it for time-based bookkeeping like the Pro grace period.
    private val freshData = MutableSharedFlow<FreshData>(replay = 1)
    val freshBillingData: Flow<FreshData> = freshData

    // Bumped whenever someone actively wants billing NOW (see useConnection): a pending reconnect
    // backoff is cut short instead of making the user wait out the timer. A generation counter
    // (compared against the value captured at attempt start) instead of an event flow, so demand
    // arriving while a connection attempt is still in flight isn't lost, while demand that was
    // already satisfied by a healthy connection can't skip a future backoff.
    private val connectionDemand = MutableStateFlow(0)

    // Highest demand generation actually served by a healthy connection (see useConnection):
    // demand that was already satisfied must not skip a backoff after a later disconnect.
    private val servedDemand = MutableStateFlow(0)

    // Consecutive failed connection attempts since the last successful one. retryWhen's `attempt`
    // counter never resets within a collection, so a long-lived process with earlier (healed)
    // failures would otherwise start every new failure episode at the maximum backoff.
    private var connectionFailStreak = 0

    // Only touched from the sharing collector (onStart/onEach/retryWhen run sequentially there).
    private var demandAtAttemptStart = 0

    // Purchase tokens we've already successfully acknowledged this process. The immutable Purchase
    // snapshot keeps reporting isAcknowledged=false until a fresh query supersedes it, so the ack
    // collector below would otherwise re-ack the same purchase on every re-emission. A token is added
    // ONLY after a successful ack, so a failed ack stays retryable. Touched only from the single ack
    // collector coroutine — no synchronization needed.
    private val ackedTokens = mutableSetOf<String>()

    private val connection = connectionProvider.connection
        .onStart { demandAtAttemptStart = connectionDemand.value }
        .onEach {
            connectionFailStreak = 0
            try {
                val fresh = it.refreshPurchases()
                freshData.emit(FreshData(BillingData(purchases = fresh.purchases), isFullSnapshot = fresh.isComplete))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "Initial purchase data refresh failed: ${e.asLog()}" }
            }
        }
        .retryWhen { cause, _ ->
            // Never give up terminally: the ack collector pins this shareIn forever, so WhileSubscribed
            // can't restart a completed upstream — a swallowed terminal failure (e.g. one transient
            // BILLING_UNAVAILABLE while Play updates itself at boot) would leave billing dead until
            // process restart. Retry with capped backoff instead; Play recovering makes this heal.
            if (cause is CancellationException) {
                false
            } else {
                connectionFailStreak++
                val backoffMs = (30_000L * connectionFailStreak).coerceAtMost(300_000L)
                log(TAG, WARN) {
                    "Billing connection failed (streak=$connectionFailStreak), retrying in ${backoffMs}ms: ${cause.asLog()}"
                }
                // Wait out the backoff, but let active billing demand short-circuit it: a user who
                // just fixed their Play situation (signed in, updated the store) shouldn't wait for
                // the timer when they tap restore/buy or reopen the upgrade screen. Only demand that
                // is newer than the failed attempt AND not yet served counts — the former limits a
                // still-waiting caller to one skip per attempt (no tight retry loop), the latter
                // keeps demand already satisfied by a healthy connection from skipping this backoff.
                withTimeoutOrNull(backoffMs) {
                    connectionDemand.first { it != demandAtAttemptStart && it > servedDemand.value }
                }
                true
            }
        }
        .setupCommonEventHandlers(TAG) { "connection" }
        .shareIn(scope, WhileSubscribed(3000L, 0L), replay = 1)

    private val purchases = connection
        .flatMapLatest { it.purchases }
        .distinctUntilChanged()
        .setupCommonEventHandlers(TAG) { "purchases" }
        .shareIn(scope, WhileSubscribed(3000L, 0L), replay = 1)

    val billingData: Flow<BillingData> = purchases
        .map { BillingData(purchases = it) }
        .shareIn(scope, WhileSubscribed(3000L, 0L), replay = 1)

    val purchaseFailures: Flow<BillingResult> = connection
        .flatMapLatest { it.purchaseFailures }
        .setupCommonEventHandlers(TAG) { "purchaseFailures" }

    init {
        // Completed purchases arriving via the PurchasesUpdatedListener are fresh Play data too.
        connection
            .flatMapLatest { it.purchaseEvents }
            .mapNotNull { event ->
                event
                    ?.takeIf { (result, _) -> result.isSuccess }
                    ?.let { (_, purchases) -> purchases?.filter { it.purchaseState == PurchaseState.PURCHASED } }
            }
            .onEach { freshData.emit(FreshData(BillingData(purchases = it), isFullSnapshot = false)) }
            .setupCommonEventHandlers(TAG) { "fresh-purchase-events" }
            .launchIn(scope)

        // Drive acks from TWO merged sources in a single collector (so ackedTokens stays
        // single-threaded, no sync needed):
        //  - `purchases`: the deduped ownership flow. Subscribing here also PINS the connection
        //    shareIn alive for the process, so billing stays connected in the background for acks.
        //  - `freshBillingData`: emitted on EVERY fresh Play round-trip (per-connection refresh,
        //    purchase event, manual refresh) and NOT distinctUntilChanged'd. This re-drives a
        //    previously-failed ack on the next refresh, since re-querying the SAME still-unacked
        //    purchase yields a content-equal snapshot that the deduped `purchases` flow would suppress
        //    — otherwise a failed ack could sit until Play's ~3-day auto-refund.
        merge(purchases, freshBillingData.map { it.data.purchases })
            .onEach { purchases ->
                purchases
                    .filter {
                        val needsAck = !it.isAcknowledged && it.purchaseToken !in ackedTokens

                        if (needsAck) log(TAG, INFO) { "Needs ACK: $it" }
                        else log(TAG) { "Already ACK'ed: $it" }

                        needsAck
                    }
                    .forEach { purchase ->
                        log(TAG, INFO) { "Acknowledging purchase: $purchase" }
                        // Retry transient failures inline (bounded, with backoff). The token is stored
                        // only on success, so a fully-failed ack stays eligible on the next fresh
                        // round-trip.
                        if (acknowledgeWithRetry(purchase)) {
                            ackedTokens.add(purchase.purchaseToken)
                        }
                    }
            }
            .setupCommonEventHandlers(TAG) { "connection-acks" }
            .retryWhen { cause, attempt ->
                if (cause is CancellationException) {
                    log(TAG) { "Ack was cancelled (appScope?) cancelled." }
                    return@retryWhen false
                }
                if (attempt > 5) {
                    log(TAG, WARN) { "Reached attempt limit: $attempt due to $cause" }
                    return@retryWhen false
                }
                if (cause !is BillingException) {
                    log(TAG, WARN) { "Unknown BillingClient exception type: $cause" }
                    return@retryWhen false
                } else {
                    log(TAG) { "BillingClient exception: $cause" }
                }

                if (cause is BillingClientException && cause.result.responseCode == BillingResponseCode.BILLING_UNAVAILABLE) {
                    log(TAG) { "Got BILLING_UNAVAILABLE while trying to ACK purchase." }
                    return@retryWhen false
                }

                log(TAG) { "Will retry ACK" }
                delay(3000 * attempt)
                true
            }
            .launchIn(scope)
    }

    // Bounded inline ack retry: recovers a transient acknowledge failure (network/service hiccup right
    // after a purchase) without depending on a fresh distinct purchases emission. Gives up on
    // BILLING_UNAVAILABLE (nothing to retry against) or after ACK_MAX_RETRIES. Returns whether the
    // purchase is now acknowledged.
    private suspend fun acknowledgeWithRetry(purchase: Purchase): Boolean {
        var attempt = 0
        while (true) {
            try {
                useConnection { acknowledgePurchase(purchase) }
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++
                val unavailable = e is BillingClientException &&
                    e.result.responseCode == BillingResponseCode.BILLING_UNAVAILABLE
                if (unavailable || attempt > ACK_MAX_RETRIES) {
                    log(TAG, ERROR) { "Giving up acknowledging $purchase after $attempt attempt(s): ${e.asLog()}" }
                    return false
                }
                log(TAG, WARN) { "Ack attempt $attempt failed for $purchase, retrying: ${e.asLog()}" }
                delay(ACK_RETRY_BASE_MS * attempt)
            }
        }
    }

    private suspend fun <T> useConnection(action: suspend BillingConnection.() -> T): T {
        // Every caller here is active demand (opening the upgrade screen, restore/buy taps, purchase
        // acks) — cut a pending reconnect backoff short. A no-op while the connection is healthy.
        val demandGen = connectionDemand.updateAndGet { it + 1 }
        try {
            return connection
                .map { action(it) }
                .take(1)
                .single()
        } finally {
            // Settled on ANY termination — success, error, or the caller's own timeout/cancel: a
            // call that is over is no longer pending demand and must not skip a much later backoff.
            servedDemand.update { served -> maxOf(served, demandGen) }
        }
    }

    suspend fun querySkus(vararg skus: Sku): Collection<SkuDetails> = useConnection {
        log(TAG) { "querySkus(): $skus..." }
        querySkus(*skus).also {
            log(TAG) { "querySkus(): $it" }
        }
    }

    // Fresh SUBS-only ownership read for the fail-closed IAP switch gate. Errors PROPAGATE (mapped
    // user-friendly) so the caller can fail closed on any failure — never swallow into an empty
    // result, which would look like "no renewing sub" and let a double purchase through.
    suspend fun querySubscriptions(): Collection<Purchase> = try {
        useConnection { querySubscriptions() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(TAG, WARN) { "querySubscriptions() failed:\n${e.asLog()}" }
        throw e.tryMapUserFriendly()
    }

    suspend fun startIapFlow(activity: Activity, sku: Sku, offer: Sku.Subscription.Offer?) {
        try {
            useConnection {
                launchBillingFlow(activity, sku, offer)
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to start IAP flow:\n${e.asLog()}" }
            throw e.tryMapUserFriendly()
        }
    }

    suspend fun refresh(): BillingData {
        log(TAG) { "refresh()" }
        // Query in the caller's context and return the result directly, so callers get the fresh
        // purchases (and any billing error) with a real happens-before instead of racing the shared
        // upgradeInfo replay cache.
        val fresh = useConnection { refreshPurchases() }
        return BillingData(purchases = fresh.purchases)
            .also { freshData.emit(FreshData(it, isFullSnapshot = fresh.isComplete)) }
    }

    companion object {
        private const val ACK_MAX_RETRIES = 3
        private const val ACK_RETRY_BASE_MS = 3_000L

        internal fun Throwable.tryMapUserFriendly(): Throwable {
            if (this !is BillingClientException) return this

            return when (result.responseCode) {
                BillingResponseCode.USER_CANCELED -> UserCanceledBillingException(this)
                BillingResponseCode.BILLING_UNAVAILABLE,
                BillingResponseCode.SERVICE_UNAVAILABLE,
                BillingResponseCode.SERVICE_DISCONNECTED,
                BillingResponseCode.SERVICE_TIMEOUT -> GplayServiceUnavailableException(this)

                BillingResponseCode.ERROR -> InternalBillingException(this)
                BillingResponseCode.NETWORK_ERROR -> NetworkBillingException(this)
                BillingResponseCode.ITEM_ALREADY_OWNED -> ItemAlreadyOwnedBillingException(this)
                else -> this
            }
        }

        val TAG: String = logTag("Upgrade", "Gplay", "Billing", "Manager")
    }
}