package eu.darken.bluemusic.util.iap

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import eu.darken.bluemusic.AppComponent
import eu.darken.bluemusic.common.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AppComponent.Scope
class IAPRepoFlow @Inject constructor(
    private val billingClientConnectionProvider: BillingClientConnectionProviderFlow,
    private val dispatcherProvider: DispatcherProvider
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)
    
    val isProVersion: Flow<Boolean> = billingClientConnectionProvider.connection
        .flatMapLatest { connection ->
            connection.purchases
        }
        .map { purchases ->
            SkuMapper.mapPurchases(purchases)
        }
        .catch { error ->
            if (error is BillingClientException && 
                error.result?.responseCode == BillingResponseCode.BILLING_UNAVAILABLE) {
                emit(false)
            } else {
                throw error
            }
        }
        .onStart { Timber.v("iapData.onStart()") }
        .onCompletion { Timber.v("iapData.onCompletion()") }
        .catch { Timber.w(it, "iapData.onError()") }
        .onEach { Timber.d("iapData.onEach(): %s", it) }
        .shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            replay = 1
        )
    
    init {
        // Auto-acknowledge purchases
        scope.launch {
            billingClientConnectionProvider.connection
                .flatMapLatest { client ->
                    client.purchases.map { purchases ->
                        purchases.map { purchase -> client to purchase }
                    }
                }
                .flatMapConcat { clientPurchasePairs ->
                    clientPurchasePairs.asFlow()
                }
                .filter { (_, purchase) ->
                    val needsAck = !purchase.isAcknowledged
                    if (needsAck) {
                        Timber.i("Needs ACK: %s", purchase)
                    } else {
                        Timber.d("Already ACK'ed: %s", purchase)
                    }
                    needsAck
                }
                .retry { attempt, error ->
                    if (error !is BillingClientException) return@retry false
                    when (error.result?.responseCode) {
                        BillingResponseCode.BILLING_UNAVAILABLE -> false
                        else -> {
                            delay(attempt * 10 * 1000L)
                            true
                        }
                    }
                }
                .collect { (client, purchase) ->
                    try {
                        Timber.i("Acknowledging purchase: %s", purchase)
                        client.acknowledgePurchase(purchase)
                        Timber.d("ACK check successful: %s", purchase)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to acknowledge purchase: %s", purchase)
                    }
                }
        }
    }
    
    fun recheck() {
        Timber.d("recheck()")
        scope.launch {
            try {
                refresh()
                Timber.d("Recheck successful")
            } catch (e: Exception) {
                Timber.e(e, "Recheck failed")
            }
        }
    }
    
    suspend fun refresh(): Boolean {
        Timber.v("refresh()")
        return try {
            val connection = billingClientConnectionProvider.connection.first()
            val purchases = connection.queryIaps()
            val isProVersion = SkuMapper.mapPurchases(purchases)
            Timber.d("refresh.success: %s", isProVersion)
            isProVersion
        } catch (e: Exception) {
            Timber.w(e, "refresh.error")
            throw e.mapExceptionsUserFriendly()
        }
    }
    
    suspend fun startIAPFlow(availableSku: AvailableSkus, activity: Activity): BillingResult {
        return try {
            val connection = billingClientConnectionProvider.connection.first()
            connection.startIAPFlow(activity, availableSku.sku)
        } catch (e: Exception) {
            Timber.w(e, "Failed to start IAP flow for $availableSku")
            throw e
        }
    }
}