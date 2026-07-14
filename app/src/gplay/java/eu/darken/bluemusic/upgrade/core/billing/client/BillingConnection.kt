package eu.darken.bluemusic.upgrade.core.billing.client

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryPurchasesAsync
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.setupCommonEventHandlers
import eu.darken.bluemusic.upgrade.core.billing.BillingManager.Companion.tryMapUserFriendly
import eu.darken.bluemusic.upgrade.core.billing.Sku
import eu.darken.bluemusic.upgrade.core.billing.SkuDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class BillingConnection(
    private val client: BillingClient,
    val purchaseEvents: Flow<Pair<BillingResult, Collection<Purchase>?>?>,
    private val failureEvents: Flow<BillingResult?> = MutableStateFlow(null),
) {

    // Last authoritative ownership snapshot: the combined result of a successful refreshPurchases().
    // A single snapshot (instead of per-product-type caches) makes sure a partial result — one
    // product type failed but the other found a purchase — still reaches the reactive purchases
    // flow, matching exactly what refreshPurchases() reported to its caller.
    private val querySnapshot = MutableStateFlow<Collection<Purchase>?>(null)

    val purchases: Flow<Collection<Purchase>> = combine(
        purchaseEvents,
        querySnapshot.filterNotNull(),
    ) { purchaseEvent, snapshot ->
        val combined = mutableSetOf<Purchase>()

        combined.addAll(snapshot)

        purchaseEvent
            ?.takeIf { (result, _) -> result.isSuccess }
            ?.let { (_, purchases) -> purchases?.filter { it.purchaseState == PurchaseState.PURCHASED } }
            ?.let { combined.addAll(it) }

        combined.sortedByDescending { it.purchaseTime }
    }.setupCommonEventHandlers(TAG) { "purchases" }

    // Non-OK results from onPurchasesUpdated (e.g. async ITEM_ALREADY_OWNED after the Play sheet
    // opened). Consumed by a single persistent collector in UpgradeRepoGplay — not an event bus.
    val purchaseFailures: Flow<BillingResult> = failureEvents.filterNotNull()

    private suspend fun queryPurchases(@BillingClient.ProductType type: String): Collection<Purchase> {
        val params = QueryPurchasesParams.newBuilder().apply {
            setProductType(type)
        }.build()
        val (billingResult, purchaseData) = client.queryPurchasesAsync(params)

        log(TAG) {
            "queryPurchases($type): code=${billingResult.isSuccess}, message=${billingResult.debugMessage}, purchaseData=${purchaseData}"
        }

        if (!billingResult.isSuccess) {
            log(TAG, WARN) { "queryPurchases() failed" }
            throw BillingClientException(billingResult)
        }

        return purchaseData
    }

    // Returns the freshly queried PURCHASED purchases so callers get a guaranteed happens-before
    // relation instead of racing the shared purchases/upgradeInfo replay caches after a refresh.
    // Tolerant of a single product-type failure: if either query finds a purchase we treat that as
    // authoritative, and only propagate an error when nothing was found AND a query failed — so the
    // caller can tell "not owned" apart from "couldn't verify".
    // The purchases of a refresh plus whether it covered both product types: a partial result (one
    // query failed) is still authoritative for what it FOUND, but must not be treated as proof of
    // absence for the type that couldn't be checked.
    data class PurchaseRefresh(
        val purchases: Collection<Purchase>,
        val isComplete: Boolean,
    )

    suspend fun refreshPurchases(): PurchaseRefresh = coroutineScope {
        log(TAG) { "refreshPurchases()" }
        val iapJob = async { queryPurchasedProducts(BillingClient.ProductType.INAPP) }
        val subJob = async { queryPurchasedProducts(BillingClient.ProductType.SUBS) }
        val iap = iapJob.await()
        val sub = subJob.await()
        log(TAG) { "Refreshed IAPs=${iap.getOrNull()}, SUBs=${sub.getOrNull()}" }
        val combined = combinePurchaseResults(iap, sub).also { querySnapshot.value = it }
        PurchaseRefresh(purchases = combined, isComplete = iap.isSuccess && sub.isSuccess)
    }

    // Never throws except on cancellation, so a single failing product-type query doesn't cancel the
    // sibling query (or the coroutineScope). The exception is already user-friendly-mapped.
    private suspend fun queryPurchasedProducts(
        @BillingClient.ProductType type: String,
    ): Result<Collection<Purchase>> = try {
        Result.success(queryPurchases(type).filter { it.purchaseState == PurchaseState.PURCHASED })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.tryMapUserFriendly())
    }

    suspend fun acknowledgePurchase(purchase: Purchase): BillingResult {
        val ack = AcknowledgePurchaseParams.newBuilder().apply {
            setPurchaseToken(purchase.purchaseToken)
        }.build()

        val ackResult = suspendCoroutine<BillingResult> { continuation ->
            client.acknowledgePurchase(ack) { continuation.resume(it) }
        }
        log(TAG) {
            "acknowledgePurchase(purchase=$purchase): code=${ackResult.responseCode}, message=${ackResult.debugMessage})"
        }

        if (!ackResult.isSuccess) {
            throw BillingClientException(ackResult)
        }
        return ackResult
    }

    suspend fun querySkus(vararg skus: Sku): Collection<SkuDetails> {
        log(TAG) { "querySkus(skus=${skus.joinToString { it.print() }})..." }
        val productList = skus.map { sku ->
            QueryProductDetailsParams.Product.newBuilder().apply {
                setProductId(sku.id)
                setProductType(
                    when (sku.type) {
                        Sku.Type.IAP -> BillingClient.ProductType.INAPP
                        Sku.Type.SUBSCRIPTION -> BillingClient.ProductType.SUBS
                    }
                )
            }.build()
        }

        val params = QueryProductDetailsParams.newBuilder().apply {
            setProductList(productList)
        }.build()

        val (result, details) = suspendCoroutine<Pair<BillingResult, Collection<ProductDetails>?>> { continuation ->
            client.queryProductDetailsAsync(params) { result: BillingResult, queryResult: QueryProductDetailsResult ->
                continuation.resume(result to queryResult.productDetailsList)
            }
        }

        log(TAG) {
            "querySkus(skus=${skus.joinToString { it.print() }}): code=${result.responseCode}, debug=${result.debugMessage}), skuDetails=$details"
        }

        if (!result.isSuccess) throw BillingClientException(result)

        if (details.isNullOrEmpty()) {
            throw IllegalStateException("No details available for ${skus.joinToString { "${it.type}-${it.id}" }}")
        }

        // Concise offer overview: makes "Play withheld an offer (e.g. trial eligibility)" vs "app
        // failed to match it" diagnosable from debug logs without wading through the full JSON.
        log(TAG) {
            val offers = details.joinToString { detail ->
                val subOffers = detail.subscriptionOfferDetails
                    ?.joinToString { "${it.basePlanId}/${it.offerId ?: "base"}" }
                "${detail.productId} -> [${subOffers ?: "one-time"}]"
            }
            "querySkus() offers: $offers"
        }

        return details
            .groupBy { it.productId }
            .mapNotNull { (key, details) ->
                val sku = skus
                    .single { it.id == key }
                val detail = details.single { it.productId == sku.id }

                SkuDetails(sku, detail)
            }
    }

    suspend fun launchBillingFlow(activity: Activity, sku: Sku, targetOffer: Sku.Subscription.Offer?): BillingResult {
        log(TAG) { "launchBillingFlow(activity=$activity, sku=$sku)" }
        if (sku.type == Sku.Type.SUBSCRIPTION) {
            requireNotNull(targetOffer) { "SUB skus require a target offer" }
        }

        val data = querySkus(sku).single { it.sku == sku }

        val params = BillingFlowParams.newBuilder().apply {
            val productDetail = BillingFlowParams.ProductDetailsParams.newBuilder().apply {
                setProductDetails(data.details)
                if (sku is Sku.Subscription && targetOffer != null) {
                    val offer = data.details.subscriptionOfferDetails!!.single {
                        targetOffer.matches(it)
                    }
                    setOfferToken(offer.offerToken)
                }
            }.build()
            setProductDetailsParamsList(listOf(productDetail))
        }.build()

        // launchBillingFlow must run on the main thread (documented BillingClient contract), and its
        // RETURNED result reports whether the flow could be launched at all (DEVELOPER_ERROR,
        // ITEM_ALREADY_OWNED, BILLING_UNAVAILABLE, ...) — failures arrive here, not as exceptions.
        // Throw like the other client calls do, so callers can surface them instead of silence.
        val result = withContext(Dispatchers.Main) {
            client.launchBillingFlow(activity, params)
        }
        log(TAG) {
            "launchBillingFlow(sku=$sku): code=${result.responseCode}, message=${result.debugMessage}"
        }
        if (!result.isSuccess) throw BillingClientException(result)

        return result
    }

    companion object {
        val TAG: String = logTag("Upgrade", "Gplay", "Billing", "ClientConnection")

        // Combines the two product-type query results: a purchase found by either type is
        // authoritative; an error is only propagated when nothing was found, so callers can tell
        // "not owned" apart from "couldn't verify one product type". Treating any found purchase as
        // authoritative is safe here because every product this app sells is a Pro SKU (see
        // OurSku.PRO_SKUS) — there are no unrelated products whose presence could mask a failed
        // query for the type that actually carries the entitlement. Pure and unit-tested.
        internal fun combinePurchaseResults(
            iap: Result<Collection<Purchase>>,
            sub: Result<Collection<Purchase>>,
        ): Collection<Purchase> {
            val found = iap.getOrNull().orEmpty() + sub.getOrNull().orEmpty()
            return when {
                found.isNotEmpty() -> found.sortedByDescending { it.purchaseTime }
                else -> {
                    (iap.exceptionOrNull() ?: sub.exceptionOrNull())?.let { throw it }
                    emptyList()
                }
            }
        }
    }
}