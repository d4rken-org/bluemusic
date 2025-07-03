package eu.darken.bluemusic.util.iap

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.jakewharton.rx3.replayingShare
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.BehaviorSubject
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
        val params = QueryPurchasesParams.newBuilder().apply {
            setProductType(BillingClient.ProductType.INAPP)
        }.build()
        client.queryPurchasesAsync(params) { result, purchases ->
            Timber.d("queryPurchasesAsync(IAP)=(code=%d, message=%s, purchases=%s)", result.responseCode, result.debugMessage, purchases)
            when {
                result.isSuccess -> {
                    emitter.onSuccess(purchases)
                    purchasePublisher.onNext(purchases)
                }

                else -> {
                    BillingClientException(result).let {
                        emitter.tryOnError(it)
                        Timber.w("queryIaps() failed")
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
                else -> emitter.tryOnError(BillingClientException(result))
            }
        }
    }

    fun querySku(sku: Sku): Single<Sku.Details> = Single.create { emitter ->
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder().apply {
                setProductId(sku.id)
                setProductType(BillingClient.ProductType.INAPP)
            }.build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        client.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
            Timber.d(
                "queryProductDetailsAsync(sku=%s): billingResult(responseCode=%d, debugMessage=%s), productDetailsResult=%s",
                sku, billingResult.responseCode, billingResult.debugMessage, productDetailsResult
            )

            val productDetailsList = productDetailsResult.productDetailsList
            when {
                billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty() -> {
                    emitter.onSuccess(Sku.Details(sku, productDetailsList))
                }

                else -> emitter.tryOnError(BillingClientException(billingResult))
            }
        }
    }

    fun startIAPFlow(activity: Activity, sku: Sku): Single<BillingResult> = querySku(sku).flatMap {
        launchBillingFlow(activity, it)
    }

    private fun launchBillingFlow(activity: Activity, skuDetails: Sku.Details): Single<BillingResult> = Single.create { emitter ->
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(skuDetails.details.single())
                .build()
        )
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()
        val result = client.launchBillingFlow(activity, billingFlowParams)
        emitter.onSuccess(result)
    }

}