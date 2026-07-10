package eu.darken.bluemusic.upgrade.core

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import eu.darken.bluemusic.common.datastore.DataStoreValue
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.upgrade.core.billing.BillingData
import eu.darken.bluemusic.upgrade.core.billing.BillingManager
import eu.darken.bluemusic.upgrade.core.billing.ItemAlreadyOwnedBillingException
import eu.darken.bluemusic.upgrade.core.billing.PurchasedSku
import eu.darken.bluemusic.upgrade.core.billing.UserCanceledBillingException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
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
import java.time.Duration
import java.time.Instant

class UpgradeRepoGplayTest : BaseTest() {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val billingManager = mockk<BillingManager>()
    private val billingCache = mockk<BillingCache>()

    private val lastProState = mockk<DataStoreValue<Long>>(relaxed = true)
    private val lastProStateSku = mockk<DataStoreValue<String>>(relaxed = true)

    // Builds a repo whose stored last-Pro timestamp is `lastProAt`. billingData is stubbed only
    // because the upgradeInfo flow references it at construction; it is never collected here.
    private fun repo(
        lastProAt: Long,
        lastSku: String = "",
        billingData: BillingData = BillingData(emptySet()),
        freshBillingData: BillingManager.FreshData? = null,
        purchaseFailures: Flow<BillingResult> = emptyFlow(),
    ): UpgradeRepoGplay {
        every { billingManager.billingData } returns flowOf(billingData)
        every { billingManager.freshBillingData } returns
            (freshBillingData?.let { flowOf(it) } ?: emptyFlow())
        every { billingManager.purchaseFailures } returns purchaseFailures
        every { lastProState.flow } returns flowOf(lastProAt)
        every { billingCache.lastProStateAt } returns lastProState
        every { lastProStateSku.flow } returns flowOf(lastSku)
        every { billingCache.lastProStateSku } returns lastProStateSku
        coJustRun { billingCache.stampLastProState(any(), any()) }
        return UpgradeRepoGplay(scope, billingManager, billingCache)
    }

    private fun proPurchase() = mockk<Purchase>().apply {
        every { products } returns OurSku.PRO_SKUS.map { it.id }
        every { purchaseTime } returns Instant.parse("2024-01-01T00:00:00Z").toEpochMilli()
    }

    @Test fun `test upgrade info pro status mapping`() {
        UpgradeRepoGplay.Info(
            gracePeriod = false,
            billingData = null
        ).apply {
            isUpgraded shouldBe false
            type shouldBe UpgradeRepo.Type.GPLAY
        }

        UpgradeRepoGplay.Info(
            gracePeriod = true,
            billingData = null
        ).isUpgraded shouldBe true

        val info = UpgradeRepoGplay.Info(
            gracePeriod = false,
            billingData = BillingData(
                purchases = setOf(
                    mockk<Purchase>().apply {
                        every { products } returns OurSku.PRO_SKUS.map { it.id }
                        every { purchaseTime } returns Instant.parse("2023-12-10T00:00:00Z").toEpochMilli()
                    }
                )
            )
        )
        info.isUpgraded shouldBe true
        info.upgradedAt shouldBe Instant.parse("2023-12-10T00:00:00Z")
    }

    @Test fun `grace period is 7 days`() {
        // Guards against the unit error where 7 * 24 * 60 * 1000 (2.8h) was used instead of 7 days,
        // which dropped paying users to non-Pro within hours of a transient empty/failed billing response.
        UpgradeRepoGplay.GRACE_PERIOD_MS shouldBe 604_800_000L
    }

    @Test fun `restore returns pro when a purchase is found`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(setOf(proPurchase()))

        repo(lastProAt = 0L).restorePurchaseNow().isUpgraded shouldBe true
    }

    @Test fun `restore keeps pro within grace when the query comes back empty`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(emptySet())

        repo(lastProAt = System.currentTimeMillis() - 1_000).restorePurchaseNow().isUpgraded shouldBe true
    }

    @Test fun `restore is not pro when the query is empty and grace has expired`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(emptySet())

        val expired = System.currentTimeMillis() - UpgradeRepoGplay.GRACE_PERIOD_MS - 1_000
        repo(lastProAt = expired).restorePurchaseNow().isUpgraded shouldBe false
    }

    @Test fun `restore keeps pro within grace when the query errors`() = runTest2 {
        coEvery { billingManager.refresh() } throws RuntimeException("Play unavailable")

        repo(lastProAt = System.currentTimeMillis() - 1_000).restorePurchaseNow().isUpgraded shouldBe true
    }

    @Test fun `restore rethrows the error when it happens outside grace`() = runTest2 {
        coEvery { billingManager.refresh() } throws RuntimeException("Play unavailable")

        shouldThrow<RuntimeException> {
            repo(lastProAt = 0L).restorePurchaseNow()
        }
    }

    @Test fun `permanent IAP keeps grace well beyond the subscription window`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(emptySet())
        // 20 days ago: past the 7-day subscription window, but within the 30-day IAP window.
        val twentyDaysAgo = System.currentTimeMillis() - Duration.ofDays(20).toMillis()

        repo(lastProAt = twentyDaysAgo, lastSku = OurSku.Iap.PRO_UPGRADE.id)
            .restorePurchaseNow().isUpgraded shouldBe true
    }

    @Test fun `subscription grace expires after the short window`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(emptySet())
        val twentyDaysAgo = System.currentTimeMillis() - Duration.ofDays(20).toMillis()

        repo(lastProAt = twentyDaysAgo, lastSku = OurSku.Sub.PRO_UPGRADE.id)
            .restorePurchaseNow().isUpgraded shouldBe false
    }

    @Test fun `legacy empty last SKU falls back to the short window`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(emptySet())
        val twentyDaysAgo = System.currentTimeMillis() - Duration.ofDays(20).toMillis()

        // Existing installs have a timestamp but no recorded SKU: they keep the old 7-day window
        // until the next successful query records one.
        repo(lastProAt = twentyDaysAgo, lastSku = "").restorePurchaseNow().isUpgraded shouldBe false
    }

    @Test fun `IAP grace window is longer than the subscription window`() {
        (UpgradeRepoGplay.GRACE_PERIOD_IAP_MS > UpgradeRepoGplay.GRACE_PERIOD_MS) shouldBe true
        UpgradeRepoGplay.GRACE_PERIOD_IAP_MS shouldBe Duration.ofDays(30).toMillis()
    }

    @Test fun `preferredProSku prefers the permanent IAP when both are owned`() {
        val iap = PurchasedSku(OurSku.Iap.PRO_UPGRADE, mockk<Purchase>())
        val sub = PurchasedSku(OurSku.Sub.PRO_UPGRADE, mockk<Purchase>())

        UpgradeRepoGplay.preferredProSku(listOf(sub, iap))?.id shouldBe OurSku.Iap.PRO_UPGRADE.id
        UpgradeRepoGplay.preferredProSku(listOf(iap))?.id shouldBe OurSku.Iap.PRO_UPGRADE.id
        UpgradeRepoGplay.preferredProSku(listOf(sub))?.id shouldBe OurSku.Sub.PRO_UPGRADE.id
        UpgradeRepoGplay.preferredProSku(emptyList()) shouldBe null
    }

    @Test fun `mapped billing data does not stamp the grace timestamp`() = runTest2 {
        // The reactive mapping can run on replayed (stale) data, e.g. when the upgrade screen is
        // reopened in a long-lived process — that must not extend the grace window.
        val repo = repo(lastProAt = 0L, billingData = BillingData(setOf(proPurchase())))

        // upgradeInfo is seeded with a null emission; wait for the purchase-mapped one, twice, so
        // the second collection is served from the shareIn replay cache.
        repo.upgradeInfo.first { it.isUpgraded }.isUpgraded shouldBe true
        repo.upgradeInfo.first { it.isUpgraded }.isUpgraded shouldBe true

        coVerify(exactly = 0) { billingCache.stampLastProState(any(), any()) }
    }

    @Test fun `fresh billing data stamps the grace timestamp`() = runTest2 {
        repo(
            lastProAt = 0L,
            freshBillingData = BillingManager.FreshData(
                BillingData(setOf(proPurchase())),
                isFullSnapshot = true,
            ),
        )

        coVerify(exactly = 1) { billingCache.stampLastProState(OurSku.Iap.PRO_UPGRADE.id, any()) }
    }

    @Test fun `fresh data without a known pro SKU does not stamp`() = runTest2 {
        val unknown = mockk<Purchase>().apply {
            every { products } returns listOf("some.unknown.product")
            every { purchaseTime } returns 1_000L
        }
        repo(
            lastProAt = 0L,
            freshBillingData = BillingManager.FreshData(BillingData(setOf(unknown)), isFullSnapshot = true),
        )

        coVerify(exactly = 0) { billingCache.stampLastProState(any(), any()) }
    }

    @Test fun `a subscription-only purchase event does not downgrade the IAP grace class`() = runTest2 {
        val subOnly = mockk<Purchase>().apply {
            every { products } returns listOf(OurSku.Sub.PRO_UPGRADE.id)
            every { purchaseTime } returns 1_000L
        }
        repo(
            lastProAt = 1_000L,
            lastSku = OurSku.Iap.PRO_UPGRADE.id,
            freshBillingData = BillingManager.FreshData(BillingData(setOf(subOnly)), isFullSnapshot = false),
        )

        // Timestamp refreshes, but the stored SKU keeps the permanent IAP's 30-day class.
        coVerify(exactly = 1) { billingCache.stampLastProState(OurSku.Iap.PRO_UPGRADE.id, any()) }
    }

    @Test fun `a full snapshot with only a subscription stamps the subscription class`() = runTest2 {
        val subOnly = mockk<Purchase>().apply {
            every { products } returns listOf(OurSku.Sub.PRO_UPGRADE.id)
            every { purchaseTime } returns 1_000L
        }
        repo(
            lastProAt = 1_000L,
            lastSku = OurSku.Iap.PRO_UPGRADE.id,
            freshBillingData = BillingManager.FreshData(BillingData(setOf(subOnly)), isFullSnapshot = true),
        )

        coVerify(exactly = 1) { billingCache.stampLastProState(OurSku.Sub.PRO_UPGRADE.id, any()) }
    }

    @Test fun `already-owned buy attempt silently restores the purchase instead of erroring`() = runTest2 {
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws
            ItemAlreadyOwnedBillingException(RuntimeException("launch result"))
        coEvery { billingManager.refresh() } returns BillingData(setOf(proPurchase()))

        val errors = mutableListOf<Throwable>()
        repo(lastProAt = 0L).launchBillingFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null) { errors.add(it) }

        errors shouldBe emptyList<Throwable>()
    }

    @Test fun `already-owned buy attempt falls back to the error dialog when restore finds nothing`() = runTest2 {
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws
            ItemAlreadyOwnedBillingException(RuntimeException("launch result"))
        coEvery { billingManager.refresh() } returns BillingData(emptySet())

        val errors = mutableListOf<Throwable>()
        // Grace expired -> the restore can't rescue the entitlement either.
        repo(lastProAt = 0L).launchBillingFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null) { errors.add(it) }

        errors.single().shouldBeInstanceOf<ItemAlreadyOwnedBillingException>()
    }

    @Test fun `already-owned buy attempt falls back to the error dialog when restore itself errors`() = runTest2 {
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws
            ItemAlreadyOwnedBillingException(RuntimeException("launch result"))
        coEvery { billingManager.refresh() } throws RuntimeException("Play unavailable")

        val errors = mutableListOf<Throwable>()
        repo(lastProAt = 0L).launchBillingFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null) { errors.add(it) }

        errors.single().shouldBeInstanceOf<ItemAlreadyOwnedBillingException>()
    }

    @Test fun `user cancel during the buy flow stays silent`() = runTest2 {
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws
            UserCanceledBillingException(RuntimeException("launch result"))

        val errors = mutableListOf<Throwable>()
        repo(lastProAt = 0L).launchBillingFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null) { errors.add(it) }

        errors shouldBe emptyList<Throwable>()
    }

    @Test fun `other buy flow failures reach the error callback`() = runTest2 {
        val failure = RuntimeException("launch failed")
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws failure

        val errors = mutableListOf<Throwable>()
        repo(lastProAt = 0L).launchBillingFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null) { errors.add(it) }

        errors.single() shouldBe failure
    }

    @Test fun `async already-owned purchase failure silently restores`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(setOf(proPurchase()))
        val alreadyOwned = BillingResult.newBuilder()
            .setResponseCode(BillingResponseCode.ITEM_ALREADY_OWNED)
            .build()

        repo(lastProAt = 0L, purchaseFailures = flowOf(alreadyOwned))

        coVerify(exactly = 1) { billingManager.refresh() }
    }

    @Test fun `other async purchase failures are ignored`() = runTest2 {
        val canceled = BillingResult.newBuilder()
            .setResponseCode(BillingResponseCode.USER_CANCELED)
            .build()

        repo(lastProAt = 0L, purchaseFailures = flowOf(canceled))

        coVerify(exactly = 0) { billingManager.refresh() }
    }
}
