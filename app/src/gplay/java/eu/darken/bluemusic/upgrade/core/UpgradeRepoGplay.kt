package eu.darken.bluemusic.upgrade.core

import android.app.Activity
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.datastore.value
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.setupCommonEventHandlers
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.upgrade.core.billing.BillingData
import eu.darken.bluemusic.upgrade.core.billing.BillingManager
import eu.darken.bluemusic.upgrade.core.billing.PurchasedSku
import eu.darken.bluemusic.upgrade.core.billing.Sku
import eu.darken.bluemusic.upgrade.core.billing.SkuDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

@Singleton
class UpgradeRepoGplay @Inject constructor(
    @param:AppScope private val scope: CoroutineScope,
    private val billingManager: BillingManager,
    private val billingCache: BillingCache,
) : UpgradeRepo {

    override val mainWebsite: String = SITE

    init {
        // Grace bookkeeping is driven by *fresh* Play query results only (per-connection/manual
        // refreshes, completed purchases) — never by the replayed billingData/upgradeInfo flows.
        // Replayed data re-running the reactive mapping must not re-stamp the timestamp, otherwise a
        // long-lived process (e.g. while the monitor service is up) could keep extending the grace
        // window from data that is weeks old.
        billingManager.freshBillingData
            .onEach { stampLastProState(it) }
            .catch { log(TAG, ERROR) { "Pro state stamping failed: ${it.asLog()}" } }
            .setupCommonEventHandlers(TAG) { "lastProState-stamping" }
            .launchIn(scope)
    }

    private suspend fun stampLastProState(data: BillingData) {
        val sku = preferredProSku(Info(billingData = data).upgrades) ?: return
        log(TAG, VERBOSE) { "Fresh Pro state confirmed by $sku, stamping." }
        // SKU before timestamp: if we die in between, the old timestamp keeps its old window class
        // instead of a fresh timestamp being interpreted with a stale SKU.
        billingCache.lastProStateSku.value(sku.id)
        billingCache.lastProStateAt.value(System.currentTimeMillis())
    }

    override val upgradeInfo: Flow<Info> = billingManager.billingData
        .map<BillingData, BillingData?> { it }
        .onStart { emit(null) }
        .setupCommonEventHandlers(TAG) { "upgradeInfo1" }
        .map { data: BillingData? -> data.toUpgradeInfo() }
        .distinctUntilChanged()
        .retryWhen { error, attempt ->
            // Ignore Google Play errors if the last pro state was recent
            val now = System.currentTimeMillis()
            val lastProStateAt = billingCache.lastProStateAt.value()
            log(TAG) { "Catch: now=$now, lastProStateAt=$lastProStateAt, attempt=$attempt, error=$error" }
            if ((now - lastProStateAt) < graceWindowMs()) {
                log(TAG, VERBOSE) { "We are not pro, but were recently, and just got an error, what is GPlay doing???" }
                emit(Info(gracePeriod = true, billingData = null))
            } else {
                emit(Info(error = error, billingData = null))
            }
            delay(30_000L * 2.0.pow(attempt.toDouble()).toLong())
            true
        }
        .setupCommonEventHandlers(TAG) { "upgradeInfo2" }
        .shareIn(scope, SharingStarted.WhileSubscribed(3000L, 0L), replay = 1)

    // True once we've ever confirmed a (known) Pro purchase on this install; drives the proactive
    // restore banner. Local signal only — a fresh install or switched Google account starts false.
    val wasEverPro: Flow<Boolean> = billingCache.lastProStateAt.flow.map { it > 0 }

    suspend fun launchBillingFlow(activity: Activity, sku: Sku, offer: Sku.Subscription.Offer?) {
        log(TAG) { "launchBillingFlow($activity,$sku)" }
        val resultChannel = Channel<Result<Unit>>()

        scope.launch {
            try {
                billingManager.startIapFlow(activity, sku, offer)
                resultChannel.send(Result.success(Unit))
            } catch (e: Exception) {
                log(TAG) { "startIapFlow failed:${e.asLog()}" }
                resultChannel.send(Result.failure(e))
            }
        }

        val result = resultChannel.receive()
        result.getOrThrow()
    }

    suspend fun querySkus(vararg skus: Sku): Collection<SkuDetails> = billingManager.querySkus(*skus)

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        try {
            billingManager.refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Background refresh: keep the old swallow-and-log behaviour so callers like MainViewModel
            // aren't affected. The explicit restore path uses restorePurchaseNow(), which surfaces errors.
            log(TAG, ERROR) { "Background refresh failed: ${e.asLog()}" }
        }
    }

    // Explicit "Restore purchase": query Play now and evaluate Pro from the returned data in the same
    // coroutine (real happens-before), so we never read a stale upgradeInfo replay. Billing errors
    // propagate so the caller can distinguish "not owned" from "Play unavailable".
    suspend fun restorePurchaseNow(): Info {
        log(TAG) { "restorePurchaseNow()" }
        return try {
            billingManager.refresh().toUpgradeInfo()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Mirror the reactive flow's retryWhen: a transient Play error while we were Pro recently
            // keeps us Pro via the grace period; otherwise surface the error so the caller can show
            // the proper "Play unavailable" message instead of a generic restore failure.
            val lastProStateAt = billingCache.lastProStateAt.value()
            if ((System.currentTimeMillis() - lastProStateAt) < graceWindowMs()) {
                log(TAG, VERBOSE) { "restore hit a Play error but we were Pro recently -> grace" }
                Info(gracePeriod = true, billingData = null)
            } else {
                throw e
            }
        }
    }

    // Shared Pro/grace mapping used by both the reactive upgradeInfo flow and restorePurchaseNow().
    // Only relinquishes Pro if we haven't had it for a while (grace period). Pure: the grace
    // timestamp is stamped by the freshBillingData collector above, not from mapped (possibly
    // replayed) flow data.
    private suspend fun BillingData?.toUpgradeInfo(): Info {
        val now = System.currentTimeMillis()
        val lastProStateAt = billingCache.lastProStateAt.value()
        log(TAG) { "toUpgradeInfo(): now=$now, lastProStateAt=$lastProStateAt, data=$this" }
        return when {
            this?.purchases?.isNotEmpty() == true -> Info(billingData = this)

            (now - lastProStateAt) < graceWindowMs() -> {
                log(TAG, VERBOSE) { "We are not pro, but were recently, did GPlay try annoy us again?" }
                Info(gracePeriod = true, billingData = null)
            }

            else -> Info(billingData = this)
        }
    }

    // Grace window depends on what was last owned: a permanent one-time purchase gets a long window,
    // a subscription (or an unknown/legacy last SKU) gets the short default.
    private suspend fun graceWindowMs(): Long {
        val lastSku = billingCache.lastProStateSku.value()
        val type = OurSku.PRO_SKUS.singleOrNull { it.id == lastSku }?.type
        val window = if (type == Sku.Type.IAP) GRACE_PERIOD_IAP_MS else GRACE_PERIOD_MS
        log(TAG) { "graceWindowMs(): lastSku=$lastSku, type=$type -> ${window}ms" }
        return window
    }

    data class Info(
        private val gracePeriod: Boolean = false,
        private val billingData: BillingData?,
        override val error: Throwable? = null,
    ) : UpgradeRepo.Info {

        override val type: UpgradeRepo.Type = UpgradeRepo.Type.GPLAY

        val upgrades: Collection<PurchasedSku> = billingData?.purchases
            ?.map { purchase ->
                purchase.products.mapNotNull { productId ->
                    val sku = OurSku.PRO_SKUS.singleOrNull { it.id == productId }
                    if (sku == null) {
                        log(TAG, ERROR) { "Unknown product: $productId ($purchase)" }
                        return@mapNotNull null
                    } else {
                        log(TAG) { "Mapped $productId to $sku ($purchase)" }
                    }
                    PurchasedSku(sku, purchase)
                }
            }
            ?.flatten()
            ?: emptySet()

        override val isUpgraded: Boolean = upgrades.isNotEmpty() || gracePeriod

        override val upgradedAt: Instant? = upgrades
            .maxByOrNull { it.purchase.purchaseTime }
            ?.let { Instant.ofEpochMilli(it.purchase.purchaseTime) }
    }


    companion object {
        private const val SITE = "https://play.google.com/store/apps/details?id=eu.darken.bluemusic"

        // Keep paying users Pro through transient empty/failed Play Billing responses. A permanent
        // one-time purchase should almost never be dropped on a hiccup, so it gets a long window; a
        // subscription legitimately lapses, so it keeps the short one. GRACE_PERIOD_MS is the
        // subscription/default window (also used when the last-owned SKU is unknown/legacy).
        val GRACE_PERIOD_MS = Duration.ofDays(7).toMillis()
        val GRACE_PERIOD_IAP_MS = Duration.ofDays(30).toMillis()

        val TAG: String = logTag("Upgrade", "Gplay", "Repo")

        // The SKU whose grace window applies when several are owned: the permanent one-time purchase
        // wins over a subscription (purchases are time-sorted, so firstOrNull alone isn't enough).
        // null when no known Pro SKU is owned.
        internal fun preferredProSku(upgrades: Collection<PurchasedSku>): Sku? =
            upgrades.firstOrNull { it.sku.type == Sku.Type.IAP }?.sku ?: upgrades.firstOrNull()?.sku
    }
}
