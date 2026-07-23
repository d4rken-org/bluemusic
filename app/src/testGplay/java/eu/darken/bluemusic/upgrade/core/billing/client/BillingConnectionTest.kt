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
                every { purchaseToken } returns "owned"
            }
            val client = mockk<BillingClient>()
            // Single-threaded test dispatcher: first query is INAPP, second is SUBS.
            coEvery { client.queryPurchasesAsync(any<QueryPurchasesParams>()) } returnsMany listOf(
                PurchasesResult(result(BillingResponseCode.OK), listOf(owned)),
                PurchasesResult(result(BillingResponseCode.ERROR), emptyList()),
            )
            val connection = BillingConnection(client, MutableStateFlow(null))

            val refresh = connection.refreshPurchases()
            refresh.purchases shouldBe listOf(owned)
            refresh.isComplete shouldBe false
            connection.purchases.first() shouldBe listOf(owned)
        } finally {
            unmockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        }
    }

    @Test fun `querySubscriptions returns the freshly queried subscriptions`() = runTest2 {
        mockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        try {
            val sub = mockk<Purchase>().apply {
                every { purchaseState } returns PurchaseState.PURCHASED
                every { purchaseTime } returns 1_000L
                every { purchaseToken } returns "sub-a"
            }
            val client = mockk<BillingClient>()
            coEvery { client.queryPurchasesAsync(any<QueryPurchasesParams>()) } returns
                PurchasesResult(result(BillingResponseCode.OK), listOf(sub))
            val connection = BillingConnection(client, MutableStateFlow(null))

            connection.querySubscriptions() shouldBe listOf(sub)
        } finally {
            unmockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        }
    }

    @Test fun `querySubscriptions keeps a snapshot sub a stale empty query missed`() = runTest2 {
        // Over-block, never miss: a renewing sub already in the last snapshot must survive a transient
        // stale-empty SUBS query so the fail-closed switch gate can't be fooled into allowing a
        // double purchase.
        mockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        try {
            val subA = mockk<Purchase>().apply {
                every { purchaseState } returns PurchaseState.PURCHASED
                every { purchaseTime } returns 1_000L
                every { purchaseToken } returns "A"
            }
            val client = mockk<BillingClient>()
            coEvery { client.queryPurchasesAsync(any<QueryPurchasesParams>()) } returnsMany listOf(
                PurchasesResult(result(BillingResponseCode.OK), emptyList()),   // refresh INAPP
                PurchasesResult(result(BillingResponseCode.OK), listOf(subA)),  // refresh SUBS -> snapshot
                PurchasesResult(result(BillingResponseCode.OK), emptyList()),   // querySubscriptions: stale empty
            )
            val connection = BillingConnection(client, MutableStateFlow(null))
            connection.refreshPurchases()

            connection.querySubscriptions() shouldBe listOf(subA)
        } finally {
            unmockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        }
    }

    @Test fun `querySubscriptions lets the fresh result win over a stale snapshot entry`() = runTest2 {
        // A sub the user just cancelled comes back from Play with isAutoRenewing=false and must
        // overwrite the stale renewing snapshot entry (same token), so the switch can unlock.
        mockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        try {
            val renewing = mockk<Purchase>().apply {
                every { purchaseState } returns PurchaseState.PURCHASED
                every { purchaseTime } returns 1_000L
                every { purchaseToken } returns "A"
                every { isAutoRenewing } returns true
            }
            val cancelled = mockk<Purchase>().apply {
                every { purchaseState } returns PurchaseState.PURCHASED
                every { purchaseTime } returns 1_000L
                every { purchaseToken } returns "A"
                every { isAutoRenewing } returns false
            }
            val client = mockk<BillingClient>()
            coEvery { client.queryPurchasesAsync(any<QueryPurchasesParams>()) } returnsMany listOf(
                PurchasesResult(result(BillingResponseCode.OK), emptyList()),    // refresh INAPP
                PurchasesResult(result(BillingResponseCode.OK), listOf(renewing)), // refresh SUBS -> snapshot
                PurchasesResult(result(BillingResponseCode.OK), listOf(cancelled)), // querySubscriptions: fresh
            )
            val connection = BillingConnection(client, MutableStateFlow(null))
            connection.refreshPurchases()

            val result = connection.querySubscriptions()
            result.map { it.isAutoRenewing } shouldBe listOf(false)
        } finally {
            unmockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        }
    }

    @Test fun `a fresh snapshot supersedes a stale purchase event with the same token`() = runTest2 {
        // A subscription bought this session (renewing event) that is then cancelled: the authoritative
        // query snapshot (isAutoRenewing=false) must supersede the stale event by token, not coexist
        // with it — otherwise the sub->IAP switch stays locked as "renewing".
        mockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        try {
            val renewing = mockk<Purchase>().apply {
                every { purchaseState } returns PurchaseState.PURCHASED
                every { purchaseTime } returns 1_000L
                every { purchaseToken } returns "A"
                every { isAutoRenewing } returns true
            }
            val cancelled = mockk<Purchase>().apply {
                every { purchaseState } returns PurchaseState.PURCHASED
                every { purchaseTime } returns 1_000L
                every { purchaseToken } returns "A"
                every { isAutoRenewing } returns false
            }
            val client = mockk<BillingClient>()
            coEvery { client.queryPurchasesAsync(any<QueryPurchasesParams>()) } returnsMany listOf(
                PurchasesResult(result(BillingResponseCode.OK), emptyList()),      // refresh INAPP
                PurchasesResult(result(BillingResponseCode.OK), listOf(cancelled)), // refresh SUBS -> snapshot
            )
            val events = MutableStateFlow<Pair<BillingResult, Collection<Purchase>?>?>(
                result(BillingResponseCode.OK) to listOf(renewing),
            )
            val connection = BillingConnection(client, events)
            connection.refreshPurchases()

            val result = connection.purchases.first()
            result.map { it.isAutoRenewing } shouldBe listOf(false)
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

    @Test fun `purchase failures carry only non-OK listener results`() = runTest2 {
        val failures = MutableStateFlow<BillingResult?>(null)
        val connection = BillingConnection(mockk(), MutableStateFlow(null), failures)

        failures.value = result(BillingResponseCode.ITEM_ALREADY_OWNED)

        connection.purchaseFailures.first().responseCode shouldBe BillingResponseCode.ITEM_ALREADY_OWNED
    }

    @Test fun `a later failure event does not remove a fresh purchase from the purchases flow`() = runTest2 {
        // Success and failure events live in separate flows: a USER_CANCELED arriving after a
        // successful purchase event must not overwrite it — the purchase may not be in the query
        // snapshot yet and would otherwise vanish from raw billing data until the next query.
        mockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        try {
            val client = mockk<BillingClient>()
            coEvery { client.queryPurchasesAsync(any<QueryPurchasesParams>()) } returns
                PurchasesResult(result(BillingResponseCode.OK), emptyList())
            val events = MutableStateFlow<Pair<BillingResult, Collection<Purchase>?>?>(null)
            val failures = MutableStateFlow<BillingResult?>(null)
            val connection = BillingConnection(client, events, failures)
            connection.refreshPurchases() // empty snapshot, predates the purchase

            val owned = mockk<Purchase>().apply {
                every { purchaseState } returns PurchaseState.PURCHASED
                every { purchaseTime } returns 1_000L
                every { purchaseToken } returns "owned"
            }
            events.value = result(BillingResponseCode.OK) to listOf(owned)
            failures.value = result(BillingResponseCode.USER_CANCELED)

            connection.purchases.first() shouldBe listOf(owned)
        } finally {
            unmockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        }
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
