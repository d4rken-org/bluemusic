package eu.darken.bluemusic.util.iap

import android.app.Activity
import com.android.billingclient.api.*
import com.jakewharton.rx.replayingShare
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import timber.log.Timber

data class BillingClientConnection(
        val client: BillingClient,
        val clientResult: BillingResult,
        private val purchasePublisher: BehaviorSubject<Collection<Purchase>>,
) {

    val purchases: Observable<Collection<Purchase>> = purchasePublisher
            .doOnSubscribe { Timber.v("purchases.onSubscribe()") }
            .doFinally { Timber.v("purchases.onFinally()") }
            .doOnError { Timber.w(it, "purchases.onError()") }
            .doOnNext { Timber.d("purchases.onNext(): %s", it) }
            .replayingShare()

    fun queryIaps(): Single<Collection<Purchase>> = Single.create { emitter ->
        client.queryPurchasesAsync(BillingClient.SkuType.INAPP) { result, purchases ->
            Timber.d("queryPurchasesAsync(IAP)=(code=%d, message=%s, purchases=%s)", result.responseCode, result.debugMessage, purchases)
            when {
                result.isSuccess -> {
                    emitter.onSuccess(purchases)
                    purchasePublisher.onNext(purchases)
                }
                else -> {
                    BillingClientException(result).let {
                        emitter.onError(it)
                        purchasePublisher.onError(it)
                    }
                }
            }
        }
    }

    fun acknowledgePurchase(purchase: Purchase): Single<BillingResult> = Single.create { emitter ->
        val ack = AcknowledgePurchaseParams.newBuilder().apply {
            setPurchaseToken(purchase.purchaseToken)
        }.build()
        client.acknowledgePurchase(ack) { result ->
            Timber.v("onAcknowledgePurchaseResponse(code=%s, message=%s)", result.responseCode, result.debugMessage)
            when {
                result.isSuccess -> emitter.onSuccess(result)
                else -> emitter.onError(BillingClientException(result))
            }
        }
    }

    fun querySku(sku: Sku): Single<Sku.Details> = Single.create { emitter ->
        val skuParams = SkuDetailsParams.newBuilder().apply {
            setType(BillingClient.SkuType.INAPP)
            setSkusList(listOf(sku.id))
        }.build()

        client.querySkuDetailsAsync(skuParams) { skuResult, skuDetails ->
            Timber.d("querySkuDetailsAsync(sku=%s): billingResult(responseCode=%d, debugMessage=%s), skuDetailsList=%s",
                    sku, skuResult.responseCode, skuResult.debugMessage, skuDetails)

            when {
                skuResult.responseCode == BillingClient.BillingResponseCode.OK && !skuDetails.isNullOrEmpty() -> {
                    emitter.onSuccess(Sku.Details(sku, skuDetails))
                }
                else -> emitter.tryOnError(BillingClientException(skuResult))
            }
        }
    }

    fun startIAPFlow(activity: Activity, sku: Sku): Single<BillingResult> = querySku(sku).flatMap {
        launchBillingFlow(activity, it)
    }

    private fun launchBillingFlow(activity: Activity, skuDetails: Sku.Details): Single<BillingResult> = Single.create { emitter ->
        val result = client.launchBillingFlow(
                activity,
                BillingFlowParams.newBuilder().setSkuDetails(skuDetails.details.single()).build()
        )
        emitter.onSuccess(result)
    }
}