package eu.darken.bluemusic.upgrade.core.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
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

    private val connection = connectionProvider.connection
        .onEach {
            try {
                val fresh = it.refreshPurchases()
                freshData.emit(FreshData(BillingData(purchases = fresh.purchases), isFullSnapshot = fresh.isComplete))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "Initial purchase data refresh failed: ${e.asLog()}" }
            }
        }
        .catch { log(TAG, ERROR) { "Unable to provide client connection:\n${it.asLog()}" } }
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

        purchases
            .onEach { purchases ->
                purchases
                    .filter {
                        val needsAck = !it.isAcknowledged

                        if (needsAck) log(TAG, INFO) { "Needs ACK: $it" }
                        else log(TAG) { "Already ACK'ed: $it" }

                        needsAck
                    }
                    .forEach {
                        log(TAG, INFO) { "Acknowledging purchase: $it" }

                        try {
                            useConnection {
                                acknowledgePurchase(it)
                            }
                        } catch (e: Exception) {
                            log(TAG, ERROR) { "Failed to ancknowledge purchase: $it\n${e.asLog()}" }
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

    private suspend fun <T> useConnection(action: suspend BillingConnection.() -> T): T = connection
        .map { action(it) }
        .take(1)
        .single()

    suspend fun querySkus(vararg skus: Sku): Collection<SkuDetails> = useConnection {
        log(TAG) { "querySkus(): $skus..." }
        querySkus(*skus).also {
            log(TAG) { "querySkus(): $it" }
        }
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