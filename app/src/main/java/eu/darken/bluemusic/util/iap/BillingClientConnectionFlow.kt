package eu.darken.bluemusic.util.iap

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import eu.darken.bluemusic.common.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BillingClientConnectionFlow(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider
) : PurchasesUpdatedListener {
    
    private val purchasesFlow = MutableSharedFlow<List<Purchase>>()
    
    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()
    
    val isConnected: Flow<Boolean> = callbackFlow {
        val listener = object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Timber.d("Billing client connected")
                    trySend(true)
                } else {
                    Timber.e("Billing setup failed: ${billingResult.debugMessage}")
                    trySend(false)
                }
            }
            
            override fun onBillingServiceDisconnected() {
                Timber.d("Billing service disconnected")
                trySend(false)
            }
        }
        
        billingClient.startConnection(listener)
        
        awaitClose {
            billingClient.endConnection()
        }
    }.shareIn(
        CoroutineScope(dispatcherProvider.io),
        SharingStarted.WhileSubscribed(5000),
        replay = 1
    )
    
    val purchases: Flow<List<Purchase>> = purchasesFlow
        .onStart {
            if (isConnected.first()) {
                emit(queryPurchases())
            }
        }
        .flowOn(dispatcherProvider.io)
    
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        Timber.d("onPurchasesUpdated: ${billingResult.responseCode}, purchases: ${purchases?.size}")
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchasesFlow.tryEmit(purchases)
        }
    }
    
    suspend fun queryIaps(): List<Purchase> = withContext(dispatcherProvider.io) {
        if (!isConnected.first()) {
            throw BillingClientException(
                BillingResult.newBuilder()
                    .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                    .build()
            )
        }
        queryPurchases()
    }
    
    private suspend fun queryPurchases(): List<Purchase> = suspendCancellableCoroutine { cont ->
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
            
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                cont.resume(purchases)
            } else {
                cont.resumeWithException(BillingClientException(billingResult))
            }
        }
    }
    
    suspend fun acknowledgePurchase(purchase: Purchase): BillingResult = suspendCancellableCoroutine { cont ->
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
            
        billingClient.acknowledgePurchase(params) { billingResult ->
            cont.resume(billingResult)
        }
    }
    
    suspend fun startIAPFlow(activity: Activity, sku: String): BillingResult = withContext(dispatcherProvider.io) {
        if (!isConnected.first()) {
            throw BillingClientException(
                BillingResult.newBuilder()
                    .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                    .build()
            )
        }
        
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(sku)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
            
        val productDetailsResult = suspendCancellableCoroutine<ProductDetailsResult> { cont ->
            billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                cont.resume(ProductDetailsResult(billingResult, productDetailsList))
            }
        }
        
        if (productDetailsResult.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            throw BillingClientException(productDetailsResult.billingResult)
        }
        
        val productDetails = productDetailsResult.productDetailsList.firstOrNull()
            ?: throw IllegalStateException("Product details not found for $sku")
            
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()
                )
            )
            .build()
            
        billingClient.launchBillingFlow(activity, flowParams)
    }
    
    private data class ProductDetailsResult(
        val billingResult: BillingResult,
        val productDetailsList: List<ProductDetails>
    )
}