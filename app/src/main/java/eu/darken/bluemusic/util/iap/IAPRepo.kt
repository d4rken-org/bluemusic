package eu.darken.bluemusic.util.iap

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.jakewharton.rx3.replayingShare
import eu.darken.bluemusic.AppComponent
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

@AppComponent.Scope
class IAPRepo @Inject constructor(
        billingClientConnectionProvider: BillingClientConnectionProvider,
) {


    private val connection = billingClientConnectionProvider.connection
    private val purchaseData = connection.switchMap { it.purchases }

    val isProVersion = purchaseData
            .observeOn(Schedulers.computation())
            .map { SkuMapper.mapPurchases(it) }
            .doOnSubscribe { Timber.v("iapData.onSubscribe()") }
            .doFinally { Timber.v("iapData.onFinally()") }
            .doOnError { Timber.w(it, "iapData.onError()") }
            .doOnNext { Timber.d("iapData.onNext(): %s", it) }
            .onErrorResumeNext { error: Throwable ->
                if (error !is BillingClientException) throw error
                when (error.result?.responseCode) {
                    BillingResponseCode.BILLING_UNAVAILABLE -> Observable.empty()
                    else -> throw error
                }
            }
            .replayingShare()

    init {
        connection
                .switchMap { client ->
                    client.purchases
                            .concatMapIterable { it }
                            .map { client to it }
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
                .flatMapSingle { (client, purchase) ->
                    Timber.i("Acknowledging purchase: %s", purchase)
                    client
                            .acknowledgePurchase(purchase)
                            .doOnError { Timber.e(it, "Failed to ancknowledge purchase: %s", purchase) }
                }
                .retryDelayed(delayFactor = 10 * 1000L) { error ->
                    if (error !is BillingClientException) return@retryDelayed false
                    when (error.result?.responseCode) {
                        BillingResponseCode.BILLING_UNAVAILABLE -> false
                        else -> true
                    }
                }
                .subscribe({
                    Timber.d("ACK check successful: %s", it)
                }, {
                    Timber.e(it, "ACK check failed")
                })
    }

    fun recheck() {
        Timber.d("recheck()")
        refresh().subscribe({
            Timber.d("Recheck successful")
        }, {
            Timber.e(it, "Recheck failed")
        })
    }

    fun refresh(): Single<Boolean> = connection
            .observeOn(Schedulers.computation())
            .firstOrError()
            .doOnSubscribe { Timber.v("refresh.onSubscribe()") }
            .flatMap { it.queryIaps() }
            .map { SkuMapper.mapPurchases(it) }
            .doOnError { Timber.w(it, "refresh.onError()") }
            .doOnSuccess { Timber.d("refresh.doOnSuccess(): %s", it) }
            .mapExceptionsUserFriendly()

    fun startIAPFlow(availableSku: AvailableSkus, activity: Activity): Single<BillingResult> = connection.firstOrError()
            .observeOn(Schedulers.computation())
            .flatMap { it.startIAPFlow(activity, availableSku.sku) }
            .doOnError { error ->
                Timber.w(error, "Failed to start IAP flow for $availableSku")
            }
            .retryDelayed(1, 2000)
            .mapExceptionsUserFriendly()

    fun buyProVersion(activity: Activity) = startIAPFlow(AvailableSkus.UPGRADE_PREMIUM, activity).subscribe({
        Timber.d("buyProVersion successful")
    }, {
        Timber.e(it, "buyProVersion failed")
    })

    companion object {
        internal fun <T : Any> Single<T>.mapExceptionsUserFriendly(): Single<T> = this.onErrorResumeNext { error ->
            if (error !is BillingClientException) {
                return@onErrorResumeNext Single.error<T>(error)
            }
            when (error.result?.responseCode) {
                BillingResponseCode.BILLING_UNAVAILABLE,
                BillingResponseCode.SERVICE_UNAVAILABLE,
                BillingResponseCode.SERVICE_DISCONNECTED,
                BillingResponseCode.SERVICE_TIMEOUT -> Single.error(GPlayServiceException(error))
                else -> Single.error(error)
            }
        }
    }
}
