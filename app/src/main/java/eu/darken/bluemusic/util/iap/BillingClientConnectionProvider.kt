package eu.darken.bluemusic.util.iap

import android.content.Context
import com.android.billingclient.api.BillingClient.*
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.jakewharton.rx.replayingShare
import eu.darken.bluemusic.AppComponent
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import timber.log.Timber
import javax.inject.Inject

@AppComponent.Scope
class BillingClientConnectionProvider @Inject constructor(
        private val context: Context
) {
    val connection: Observable<BillingClientConnection> = Observable
            .create<BillingClientConnection> { clientEmitter ->
                val purchasePublisher = BehaviorSubject.create<Collection<Purchase>>()

                val client = newBuilder(context).apply {
                    enablePendingPurchases()
                    setListener { result, purchases ->
                        if (result.isSuccess) {
                            Timber.d("onPurchasesUpdated(code=%d, message=%s, purchases=%s)", result.responseCode, result.debugMessage, purchases)
                            purchasePublisher.onNext(purchases.orEmpty())
                        } else {
                            Timber.w("error: onPurchasesUpdated(code=%d, message=%s, purchases=%s)", result.responseCode, result.debugMessage, purchases)
                        }
                    }
                }.build()
                var canceled = false
                clientEmitter.setCancellable {
                    Timber.d("Stopping billing client connection")
                    canceled = true
                    client.endConnection()
                    purchasePublisher.onComplete()
                }

                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        Timber.v("onBillingSetupFinished(code=%d, message=%s)", result.responseCode, result.debugMessage)

                        when (result.responseCode) {
                            BillingResponseCode.OK -> {
                                val connection = BillingClientConnection(client, result, purchasePublisher).apply {
                                    queryIaps().subscribeOn(Schedulers.io()).subscribe(
                                            { Timber.d("Initial IAP query successful.") },
                                            { Timber.e(it, "Initial IAP query failed.") }
                                    )
                                }
                                clientEmitter.onNext(connection)
                            }
                            else -> clientEmitter.tryOnError(BillingClientException(result))
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        Timber.v("onBillingServiceDisconnected() [canceled=$canceled]")
                        // It's unclear whether this canceled check is necessary
                        if (canceled) {
                            clientEmitter.onComplete()
                        } else {
                            clientEmitter.tryOnError(BillingClientException(null))
                        }
                    }
                })
            }
            .subscribeOn(Schedulers.io())
            .doOnSubscribe { Timber.v("billingClient.onSubscribe()") }
            .doFinally { Timber.v("billingClient.onFinally()") }
            .doOnError { Timber.w(it, "billingClient.onError()") }
            .doOnNext { Timber.d("billingClient.onNext(): %s", it) }
            .retryDelayed(delayFactor = 3000) { error ->
                if (error !is BillingClientException) return@retryDelayed false
                when (error.result?.responseCode) {
                    BillingResponseCode.BILLING_UNAVAILABLE -> false
                    else -> true
                }
            }
            .replayingShare()

}