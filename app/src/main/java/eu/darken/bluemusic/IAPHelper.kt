package eu.darken.bluemusic

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.android.billingclient.api.Purchase.PurchasesResult
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import timber.log.Timber
import java.util.*
import javax.inject.Inject

@AppComponent.Scope
class IAPHelper @Inject constructor(
        context: Context
) : PurchasesUpdatedListener, BillingClientStateListener {

    private val upgradesPublisher = BehaviorSubject.create<List<Upgrade>>()

    private val client: BillingClient = BillingClient.newBuilder(context).apply {
        Timber.d("BillingClient init")
        setListener(this@IAPHelper)
        enablePendingPurchases()
    }.build().also { it.startConnection(this) }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        Timber.d("onBillingSetupFinished(billingResult=%s)", billingResult)
        if (!billingResult.isSuccessful) return

        val purchasesResult = client.queryPurchases(BillingClient.SkuType.INAPP)
        Timber.d("queryPurchases(): code=%d, purchases=%s", purchasesResult.responseCode, purchasesResult.purchasesList)
        onPurchasesUpdated(purchasesResult.billingResult, purchasesResult.purchasesList)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        Timber.d("onPurchasesUpdated(billingResult=%s, purchases=%s)", billingResult, purchases)
        if (!billingResult.isSuccessful || purchases.isNullOrEmpty()) return

        Timber.d("notifyOfPurchases(%s)", purchases)

        purchases.filter { !it.isAcknowledged }.forEach { purchase ->
            val ackParams = AcknowledgePurchaseParams.newBuilder().apply {
                setPurchaseToken(purchase.purchaseToken)
            }.build()

            client.acknowledgePurchase(ackParams) {
                if (it.isSuccessful) {
                    Timber.i("Successful acknowledgePurchase(%s): %s", purchase, it.toDebugString())
                } else {
                    Timber.e("Failed acknowledgePurchase(%s): %s", purchase, it.toDebugString())
                }
            }
        }

        val upgrades = purchases.map { Upgrade(it) }
        upgradesPublisher.onNext(upgrades)
    }

    override fun onBillingServiceDisconnected() {
        Timber.d("onBillingServiceDisconnected()")
    }

    fun check() {
        Single
                .create<PurchasesResult> { it.onSuccess(client.queryPurchases(BillingClient.SkuType.INAPP)) }
                .subscribeOn(Schedulers.io())
                .subscribe(
                        { onPurchasesUpdated(it.billingResult, it.purchasesList) },
                        { Timber.e(it) }
                )
    }

    val isProVersion: Observable<Boolean>
        get() = upgradesPublisher.map { upgrades: List<Upgrade> ->
            var proVersion = false
            for (upgrade in upgrades) {
                if (upgrade.type == Upgrade.Type.PRO_VERSION) {
                    proVersion = true
                    break
                }
            }
            proVersion
        }

    fun buyProVersion(activity: Activity) {
        val skuDetailsParam = SkuDetailsParams.newBuilder().apply {
            setType(BillingClient.SkuType.INAPP)
            setSkusList(listOf(SKU_UPGRADE))
        }.build()

        client.querySkuDetailsAsync(skuDetailsParam) { result, skus ->
            if (!result.isSuccessful) return@querySkuDetailsAsync
            if (skus.isNullOrEmpty()) return@querySkuDetailsAsync

            val flowParameters = BillingFlowParams.newBuilder().setSkuDetails(skus.first()).build()
            client.launchBillingFlow(activity, flowParameters)
        }
    }

    companion object {
        val BillingResult.isSuccessful
            get() = responseCode == BillingClient.BillingResponseCode.OK

        fun BillingResult.toDebugString() = "BillingResult(code=$responseCode, message=$debugMessage)"

        class Upgrade internal constructor(purchase: Purchase) {
            enum class Type {
                PRO_VERSION, UNKNOWN
            }

            val type: Type = if (purchase.sku.endsWith(SKU_UPGRADE)) Type.PRO_VERSION else Type.UNKNOWN
        }

        const val SKU_UPGRADE = "upgrade.premium"
    }
}