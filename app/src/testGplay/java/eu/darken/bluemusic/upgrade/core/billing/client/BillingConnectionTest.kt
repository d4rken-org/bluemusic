package eu.darken.bluemusic.upgrade.core.billing.client

import android.text.TextUtils
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import com.android.billingclient.api.PurchasesResult
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryPurchasesAsync
import eu.darken.bluemusic.upgrade.core.OurSku
import eu.darken.bluemusic.upgrade.core.billing.SkuDetails
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class BillingConnectionTest : BaseTest() {

    private fun purchase(time: Long) = mockk<Purchase>().apply { every { purchaseTime } returns time }

    private fun result(code: Int): BillingResult = BillingResult.newBuilder().setResponseCode(code).build()

    @Test fun `combines both product types, newest first`() {
        val older = purchase(1_000)
        val newer = purchase(2_000)

        BillingConnection.combinePurchaseResults(
            iap = Result.success(listOf(older)),
            sub = Result.success(listOf(newer)),
        ) shouldBe listOf(newer, older)
    }

    @Test fun `a single product-type failure does not mask a purchase found by the other`() {
        val owned = purchase(1_000)

        BillingConnection.combinePurchaseResults(
            iap = Result.success(listOf(owned)),
            sub = Result.failure(RuntimeException("SUBS query failed")),
        ) shouldBe listOf(owned)

        BillingConnection.combinePurchaseResults(
            iap = Result.failure(RuntimeException("IAP query failed")),
            sub = Result.success(listOf(owned)),
        ) shouldBe listOf(owned)
    }

    @Test fun `both product types empty returns empty`() {
        BillingConnection.combinePurchaseResults(
            iap = Result.success(emptyList()),
            sub = Result.success(emptyList()),
        ) shouldBe emptyList()
    }

    @Test fun `nothing found but a query failed rethrows the error`() {
        shouldThrow<RuntimeException> {
            BillingConnection.combinePurchaseResults(
                iap = Result.success(emptyList()),
                sub = Result.failure(RuntimeException("SUBS query failed")),
            )
        }
    }

    @Test fun `a partial-failure refresh still reaches the reactive purchases flow`() = runTest2 {
        // A purchase found by one product type while the other query fails must not leave the
        // reactive purchases/upgradeInfo chain starved — otherwise a successful restore would never
        // actually unlock the app.
        mockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        try {
            val owned = mockk<Purchase>().apply {
                every { purchaseState } returns PurchaseState.PURCHASED
                every { purchaseTime } returns 1_000L
            }
            val client = mockk<BillingClient>()
            // Single-threaded test dispatcher: first query is INAPP, second is SUBS.
            coEvery { client.queryPurchasesAsync(any<QueryPurchasesParams>()) } returnsMany listOf(
                PurchasesResult(result(BillingResponseCode.OK), listOf(owned)),
                PurchasesResult(result(BillingResponseCode.ERROR), emptyList()),
            )
            val connection = BillingConnection(client, MutableStateFlow(null))

            connection.refreshPurchases() shouldBe listOf(owned)
            connection.purchases.first() shouldBe listOf(owned)
        } finally {
            unmockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        }
    }

    // The BillingFlowParams builder calls TextUtils.isEmpty, which is an unmocked Android stub in
    // plain JVM tests — give it its real behavior.
    private fun mockTextUtils() {
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(anyNullable()) } answers { firstArg<CharSequence?>().isNullOrEmpty() }
    }

    @Test fun `launch billing flow throws on a non-OK launch result`() = runTest2 {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockTextUtils()
        try {
            val client = mockk<BillingClient>()
            val connection = spyk(BillingConnection(client, MutableStateFlow(null)))
            val details = mockk<ProductDetails>(relaxed = true).apply {
                every { productId } returns OurSku.Iap.PRO_UPGRADE.id
                every { subscriptionOfferDetails } returns null
            }
            coEvery { connection.querySkus(*anyVararg()) } returns
                listOf(SkuDetails(sku = OurSku.Iap.PRO_UPGRADE, details = details))
            every { client.launchBillingFlow(any(), any()) } returns
                result(BillingResponseCode.ITEM_ALREADY_OWNED)

            val ex = shouldThrow<BillingClientException> {
                connection.launchBillingFlow(mockk(), OurSku.Iap.PRO_UPGRADE, null)
            }
            ex.result.responseCode shouldBe BillingResponseCode.ITEM_ALREADY_OWNED
        } finally {
            unmockkStatic(TextUtils::class)
            Dispatchers.resetMain()
        }
    }

    @Test fun `launch billing flow returns the result when the launch succeeds`() = runTest2 {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockTextUtils()
        try {
            val client = mockk<BillingClient>()
            val connection = spyk(BillingConnection(client, MutableStateFlow(null)))
            val details = mockk<ProductDetails>(relaxed = true).apply {
                every { productId } returns OurSku.Iap.PRO_UPGRADE.id
                every { subscriptionOfferDetails } returns null
            }
            coEvery { connection.querySkus(*anyVararg()) } returns
                listOf(SkuDetails(sku = OurSku.Iap.PRO_UPGRADE, details = details))
            every { client.launchBillingFlow(any(), any()) } returns result(BillingResponseCode.OK)

            val launchResult = connection.launchBillingFlow(mockk(), OurSku.Iap.PRO_UPGRADE, null)

            launchResult.responseCode shouldBe BillingResponseCode.OK
        } finally {
            unmockkStatic(TextUtils::class)
            Dispatchers.resetMain()
        }
    }
}
