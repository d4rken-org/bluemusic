package eu.darken.bluemusic.upgrade.core.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import eu.darken.bluemusic.upgrade.core.OurSku
import eu.darken.bluemusic.upgrade.core.billing.client.BillingClientException
import eu.darken.bluemusic.upgrade.core.billing.client.BillingConnection
import eu.darken.bluemusic.upgrade.core.billing.client.BillingConnectionProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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
        refreshComplete: Boolean = true,
        failures: Flow<BillingResult> = emptyFlow(),
    ) = mockk<BillingConnection>().apply {
        coEvery { refreshPurchases() } returnsMany
            refreshResults.map { BillingConnection.PurchaseRefresh(it, isComplete = refreshComplete) }
        every { purchases } returns emptyFlow()
        every { purchaseEvents } returns events
        every { purchaseFailures } returns failures
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
        manager.freshBillingData.first() shouldBe BillingManager.FreshData(refreshed, isFullSnapshot = true)
    }

    @Test fun `a partial refresh is not labeled a full snapshot`() = runTest2 {
        val owned = purchase()
        val manager = manager(
            connection(refreshResults = listOf(emptyList(), listOf(owned)), refreshComplete = false)
        )

        manager.refresh()

        manager.freshBillingData.first().isFullSnapshot shouldBe false
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

        manager.freshBillingData.first() shouldBe
            BillingManager.FreshData(BillingData(listOf(owned)), isFullSnapshot = false)
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
        manager.freshBillingData.first() shouldBe
            BillingManager.FreshData(BillingData(listOf(owned)), isFullSnapshot = true)
    }

    // A manager whose connection fails launchBillingFlow with the given launch-result code —
    // the path Play uses for immediate "buy" failures (returned result, not an exception).
    private fun launchFailingManager(code: Int): BillingManager {
        val connection = mockk<BillingConnection>().apply {
            coEvery { refreshPurchases() } returns
                BillingConnection.PurchaseRefresh(emptyList(), isComplete = true)
            every { purchases } returns emptyFlow()
            every { purchaseEvents } returns emptyFlow()
            coEvery { launchBillingFlow(any(), any(), null) } throws
                BillingClientException(BillingResult.newBuilder().setResponseCode(code).build())
        }
        return manager(connection)
    }

    @Test fun `already-owned launch failure maps to ItemAlreadyOwnedBillingException`() = runTest2 {
        shouldThrow<ItemAlreadyOwnedBillingException> {
            launchFailingManager(BillingResponseCode.ITEM_ALREADY_OWNED)
                .startIapFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null)
        }
    }

    @Test fun `user cancel from the launch result maps to UserCanceledBillingException`() = runTest2 {
        shouldThrow<UserCanceledBillingException> {
            launchFailingManager(BillingResponseCode.USER_CANCELED)
                .startIapFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null)
        }
    }

    @Test fun `billing-unavailable launch failure maps to the service error`() = runTest2 {
        shouldThrow<GplayServiceUnavailableException> {
            launchFailingManager(BillingResponseCode.BILLING_UNAVAILABLE)
                .startIapFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null)
        }
    }

    @Test fun `network launch failure maps to NetworkBillingException`() = runTest2 {
        shouldThrow<NetworkBillingException> {
            launchFailingManager(BillingResponseCode.NETWORK_ERROR)
                .startIapFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null)
        }
    }

    @Test fun `developer errors are rethrown unmapped`() = runTest2 {
        shouldThrow<BillingClientException> {
            launchFailingManager(BillingResponseCode.DEVELOPER_ERROR)
                .startIapFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null)
        }
    }

    @Test fun `non-OK purchase events are exposed as purchase failures`() = runTest2 {
        val alreadyOwned = BillingResult.newBuilder()
            .setResponseCode(BillingResponseCode.ITEM_ALREADY_OWNED)
            .build()
        val manager = manager(
            connection(
                refreshResults = listOf(emptyList()),
                failures = flowOf(alreadyOwned),
            )
        )

        manager.purchaseFailures.first() shouldBe alreadyOwned
    }

    @Test fun `connection failures retry instead of killing billing`() = runTest2 {
        // The old catch{log} swallowed terminal connection failures; with the ack collector pinning
        // the shareIn subscription forever, billing stayed dead until process restart.
        val owned = purchase()
        var attempts = 0
        // First refresh result feeds the initial per-connection refresh, second the manual one.
        val connection = connection(refreshResults = listOf(emptyList(), listOf(owned)))
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow {
                attempts++
                if (attempts == 1) throw BillingException("Play is updating itself")
                emit(connection)
            }
        }
        val manager = BillingManager(backgroundScope, provider)

        val refreshed = async { manager.refresh() }
        advanceTimeBy(31_000) // past the first 30s retry backoff
        advanceUntilIdle()

        refreshed.await() shouldBe BillingData(listOf(owned))
        attempts shouldBe 2
    }
}
