package eu.darken.bluemusic.upgrade.core.billing

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import eu.darken.bluemusic.upgrade.core.billing.client.BillingConnection
import eu.darken.bluemusic.upgrade.core.billing.client.BillingConnectionProvider
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class BillingManagerTest : BaseTest() {

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun purchase() = mockk<Purchase>().apply {
        every { purchaseState } returns PurchaseState.PURCHASED
        every { purchaseTime } returns 1_000L
        every { isAcknowledged } returns true
    }

    private fun connection(
        refreshResults: List<Collection<Purchase>>,
        events: Flow<Pair<BillingResult, Collection<Purchase>?>?> = emptyFlow(),
    ) = mockk<BillingConnection>().apply {
        coEvery { refreshPurchases() } returnsMany refreshResults
        every { purchases } returns emptyFlow()
        every { purchaseEvents } returns events
    }

    private fun manager(connection: BillingConnection): BillingManager {
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flowOf(connection)
        }
        return BillingManager(scope, provider)
    }

    @Test fun `manual refresh returns and emits fresh billing data`() = runTest2 {
        val owned = purchase()
        // First result feeds the initial per-connection refresh, second the manual one.
        val manager = manager(connection(refreshResults = listOf(emptyList(), listOf(owned))))

        val refreshed = manager.refresh()

        refreshed shouldBe BillingData(listOf(owned))
        manager.freshBillingData.first() shouldBe refreshed
    }

    @Test fun `completed purchase events emit fresh billing data`() = runTest2 {
        val owned = purchase()
        val ok = BillingResult.newBuilder().setResponseCode(BillingResponseCode.OK).build()
        val manager = manager(
            connection(
                refreshResults = listOf(emptyList()),
                events = flowOf(ok to listOf(owned)),
            )
        )

        manager.freshBillingData.first() shouldBe BillingData(listOf(owned))
    }

    @Test fun `failed purchase events do not emit fresh billing data`() = runTest2 {
        val owned = purchase()
        val error = BillingResult.newBuilder().setResponseCode(BillingResponseCode.ERROR).build()
        val manager = manager(
            connection(
                refreshResults = listOf(listOf(owned)),
                events = flowOf(error to listOf(purchase())),
            )
        )

        // Only the initial refresh result may arrive, never the failed event's payload.
        manager.freshBillingData.first() shouldBe BillingData(listOf(owned))
    }
}
