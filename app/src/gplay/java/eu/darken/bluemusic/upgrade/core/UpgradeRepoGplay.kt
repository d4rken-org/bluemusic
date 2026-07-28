package eu.darken.bluemusic.upgrade.core

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.Purchase
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.datastore.value
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.setupCommonEventHandlers
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.upgrade.core.billing.BillingData
import eu.darken.bluemusic.upgrade.core.billing.BillingManager
import eu.darken.bluemusic.upgrade.core.billing.ItemAlreadyOwnedBillingException
import eu.darken.bluemusic.upgrade.core.billing.PurchasedSku
import eu.darken.bluemusic.upgrade.core.billing.Sku
import eu.darken.bluemusic.upgrade.core.billing.SkuDetails
import eu.darken.bluemusic.upgrade.core.billing.UserCanceledBillingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpgradeRepoGplay @Inject constructor(
    @param:AppScope private val scope: CoroutineScope,
    private val billingManager: BillingManager,
    private val billingCache: BillingCache,
) : UpgradeRepo {

    override val storeSite: String = STORE_SITE
    override val upgradeSite: String = UPGRADE_SITE
    override val betaSite: String = BETA_SITE

    // Counter, not a flag: overlapping already-owned recoveries (buy taps racing the UI disable)
    // must keep the busy signal up until the LAST one finishes.
    private val autoRestoring = MutableStateFlow(0)

    // The already-owned auto-restores below run invisibly on AppScope; expose their busy state so
    // the UI can pause entitlement actions instead of racing them with a manual restore or a buy.
    val autoRestoreBusy: Flow<Boolean> = autoRestoring.map { it > 0 }

    init {
        // Grace bookkeeping is driven by *fresh* Play query results only (per-connection/manual
        // refreshes, completed purchases) — never by the replayed billingData/upgradeInfo flows.
        // Replayed data re-running the reactive mapping must not re-stamp the timestamp, otherwise a
        // long-lived process (e.g. while the monitor service is up) could keep extending the grace
        // window from data that is weeks old.
        billingManager.freshBillingData
            .onEach { fresh ->
                try {
                    stampLastProState(fresh)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Isolate per-emission failures: one failed write must not kill this permanent
                    // collector and stop all future grace bookkeeping.
                    log(TAG, ERROR) { "Pro state stamping failed: ${e.asLog()}" }
                }
            }
            .setupCommonEventHandlers(TAG) { "lastProState-stamping" }
            .launchIn(scope)

        // Async variant of the launch-result ITEM_ALREADY_OWNED case: Play told us mid-flow that the
        // user already owns it. Reconcile silently — Play shows its own UI for purchase-sheet
        // failures, so no app-side dialog here.
        billingManager.purchaseFailures
            .filter { it.responseCode == BillingResponseCode.ITEM_ALREADY_OWNED }
            .onEach {
                log(TAG, INFO) { "Async already-owned event -> restoring purchase" }
                autoRestoring.update { it + 1 }
                try {
                    withTimeoutOrNull(RESTORE_ON_OWNED_TIMEOUT_MS) { restorePurchaseNow() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, WARN) { "Async already-owned restore failed: ${e.asLog()}" }
                } finally {
                    autoRestoring.update { it - 1 }
                }
            }
            .setupCommonEventHandlers(TAG) { "asyncAlreadyOwned" }
            .launchIn(scope)
    }

    private suspend fun stampLastProState(fresh: BillingManager.FreshData) {
        val sku = preferredProSku(Info(billingData = fresh.data).upgrades) ?: return
        // Bounded + fail-soft: a hung/full-disk DataStore must not stall this permanent
        // freshBillingData collector (which would eventually backpressure all later fresh emissions).
        val storedSkuId = readSafe("lastProStateSku") { billingCache.lastProStateSku.value() }
        val storedType = OurSku.PRO_SKUS.singleOrNull { it.id == storedSkuId }?.type
        // A purchase event is not a full ownership snapshot: a subscription-only event must not
        // downgrade the grace class of a previously confirmed permanent IAP. Full query snapshots
        // are authoritative and always win.
        val effectiveSkuId = if (
            !fresh.isFullSnapshot && storedType == Sku.Type.IAP && sku.type != Sku.Type.IAP
        ) {
            storedSkuId ?: sku.id
        } else {
            sku.id
        }
        log(TAG, VERBOSE) { "Fresh Pro state confirmed by $sku, stamping $effectiveSkuId." }
        try {
            withTimeoutOrNull(CACHE_WRITE_TIMEOUT_MS) {
                billingCache.stampLastProState(effectiveSkuId, System.currentTimeMillis())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "stampLastProState write failed: ${e.asLog()}" }
        }
    }

    private val upgradeInfoRaw: Flow<Info> = billingManager.billingData
        .map<BillingData, BillingData?> { it }
        .onStart { emit(null) }
        .setupCommonEventHandlers(TAG) { "upgradeInfo1" }
        .map { data: BillingData? -> data.toUpgradeInfo() }
        .distinctUntilChanged()
        .retryWhen { error, attempt ->
            // Ignore Google Play errors if the last pro state was recent
            val now = System.currentTimeMillis()
            val lastProStateAt = readLastProStateAtSafe()
            log(TAG) { "Catch: now=$now, lastProStateAt=$lastProStateAt, attempt=$attempt, error=$error" }
            if ((now - lastProStateAt) < graceWindowMs()) {
                log(TAG, VERBOSE) { "We are not pro, but were recently, and just got an error, what is GPlay doing???" }
                emit(Info(gracePeriod = true, billingData = null))
            } else {
                emit(Info(error = error, billingData = null))
            }
            // Integer, capped backoff: the old 2.0.pow(attempt) formula slept for hours after a handful
            // of failures and eventually overflowed Long into a delay(negative) hot loop.
            delay((30_000L * (attempt + 1)).coerceAtMost(300_000L))
            true
        }
        .setupCommonEventHandlers(TAG) { "upgradeInfo2" }
        .shareIn(scope, SharingStarted.WhileSubscribed(3000L, 0L), replay = 1)

    // True once we've ever confirmed a (known) Pro purchase on this install; drives the proactive
    // restore banner. Local signal only — a fresh install or switched Google account starts false.
    val wasEverPro: Flow<Boolean> = billingCache.lastProStateAt.flow
        .map { it > 0 }
        // Fail-soft: a broken/full-disk DataStore must not error out this flow (and the combine that
        // reads it) — degrade to "not previously pro" instead.
        .catch { e -> log(TAG, WARN) { "wasEverPro read failed: ${e.asLog()}" }; emit(false) }
        .distinctUntilChanged()

    // When we last confirmed a (known) Pro purchase from fresh Play data. Drives the two-stage grace
    // display: the quiet "confirming your purchase" stage becomes the diagnostics stage once the
    // entitlement has been unconfirmed past GRACE_DIAGNOSTICS_AFTER_MS. Reusing this timestamp (set at
    // confirmation, frozen when the entitlement lapses) instead of a separate "unconfirmed episode"
    // stamp means the diagnostics boundary can't get stuck during a Play outage.
    val lastProStateAt: Flow<Long> = billingCache.lastProStateAt.flow
        .catch { e -> log(TAG, WARN) { "lastProStateAt read failed: ${e.asLog()}" }; emit(0L) }
        .distinctUntilChanged()

    // False until the first authoritative Play round-trip settles OR a fallback timeout elapses, then
    // true. The fallback flow is upstream-INDEPENDENT so a dead Play can't pin the UI on Loading
    // forever. NOTE: settled != ownership-known — a fallback-driven settle can flip true before
    // ownership is known, so every purchase action reachable while settled must be independently
    // double-bill-safe (the IAP button via the fail-closed gate, the sub button via ITEM_ALREADY_OWNED).
    val isSettled: Flow<Boolean> = merge(
        billingManager.freshBillingData.map { true },
        flow {
            delay(SETTLE_FALLBACK_MS)
            emit(true)
        },
    )
        .onStart { emit(false) }
        .distinctUntilChanged()

    // Settledness rides every Info emission (canonical UpgradeRepo.Info shape) so it can never be
    // observed out of step with the ownership data it describes. Until the core port lands it is
    // still derived from the repo-level settle signal above.
    override val upgradeInfo: Flow<Info> = combine(
        upgradeInfoRaw,
        isSettled,
    ) { info, settled -> info.copy(isSettled = settled) }

    // Fresh SUBS-only Play read for the fail-closed IAP switch gate. Errors propagate (see
    // BillingManager.querySubscriptions) so the caller fails closed on any failure.
    suspend fun queryCurrentSubscriptions(): Collection<Purchase> = billingManager.querySubscriptions()

    fun launchBillingFlow(
        activity: Activity,
        sku: Sku,
        offer: Sku.Subscription.Offer?,
        onError: (Throwable) -> Unit,
    ) {
        log(TAG) { "launchBillingFlow($activity,$sku)" }
        // AppScope on purpose: the purchase flow and the already-owned recovery below must survive
        // the upgrade screen being closed; the reactive isPro emission unlocks the app either way.
        scope.launch {
            try {
                billingManager.startIapFlow(activity, sku, offer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                when {
                    e is UserCanceledBillingException -> log(TAG) { "User canceled billing flow" }

                    e is ItemAlreadyOwnedBillingException -> {
                        // Stale local state: Play says they already own it, so tapping "buy" really
                        // means "unlock what I own" — restore instead of showing an error.
                        log(TAG, INFO) { "Launch says already owned -> restoring purchase" }
                        autoRestoring.update { it + 1 }
                        val restored = try {
                            withTimeoutOrNull(RESTORE_ON_OWNED_TIMEOUT_MS) { restorePurchaseNow() }
                        } catch (re: CancellationException) {
                            throw re
                        } catch (re: Exception) {
                            log(TAG, WARN) { "Restore after already-owned failed: ${re.asLog()}" }
                            null
                        } finally {
                            autoRestoring.update { it - 1 }
                        }
                        if (restored?.isPro != true) {
                            // Couldn't reconcile the entitlement (pending purchase, account mismatch,
                            // Play quirk) — fall back to the already-owned dialog with restore tips.
                            onError(e)
                        }
                    }

                    else -> {
                        log(TAG) { "startIapFlow failed:${e.asLog()}" }
                        onError(e)
                    }
                }
            }
        }
    }

    suspend fun querySkus(vararg skus: Sku): Collection<SkuDetails> = billingManager.querySkus(*skus)

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        try {
            // Bounded: with unbounded connection retry, an unavailable Play would otherwise keep
            // background callers (MainViewModel) suspended indefinitely.
            withTimeoutOrNull(REFRESH_TIMEOUT_MS) { billingManager.refresh() }
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
        // Both outcomes below are the result of a completed Play round-trip, so they are settled.
        return try {
            billingManager.refresh().toUpgradeInfo().copy(isSettled = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Mirror the reactive flow's retryWhen: a transient Play error while we were Pro recently
            // keeps us Pro via the grace period; otherwise surface the error so the caller can show
            // the proper "Play unavailable" message instead of a generic restore failure.
            val lastProStateAt = readLastProStateAtSafe()
            if ((System.currentTimeMillis() - lastProStateAt) < graceWindowMs()) {
                log(TAG, VERBOSE) { "restore hit a Play error but we were Pro recently -> grace" }
                Info(gracePeriod = true, billingData = null, isSettled = true)
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
        // A confirmed KNOWN Pro purchase wins IMMEDIATELY, before any grace-cache read: never gate a
        // real entitlement behind a (possibly hung/failing, e.g. full-disk) DataStore read. Branch on
        // MAPPED upgrades, not the raw purchase list — a purchase of only unrecognized product IDs
        // maps to zero upgrades and must fall through to the grace window, not masquerade as Pro.
        val confirmed = Info(billingData = this)
        if (confirmed.upgrades.isNotEmpty()) return confirmed

        // Grace fallback: bounded + fail-soft reads so a broken DataStore can neither hang nor throw
        // out of this reactive mapping (which would loop the flow and stick the screen on loading).
        val now = System.currentTimeMillis()
        val lastProStateAt = readLastProStateAtSafe()
        log(TAG) { "toUpgradeInfo(): now=$now, lastProStateAt=$lastProStateAt, data=$this" }
        return if ((now - lastProStateAt) < graceWindowMs()) {
            log(TAG, VERBOSE) { "We are not pro, but were recently, did GPlay try annoy us again?" }
            Info(gracePeriod = true, billingData = null)
        } else {
            confirmed
        }
    }

    // Grace window depends on what was last owned: a permanent one-time purchase gets a long window,
    // a subscription (or an unknown/legacy/unreadable last SKU) gets the short default.
    private suspend fun graceWindowMs(): Long {
        val lastSku = readSafe("lastProStateSku") { billingCache.lastProStateSku.value() }
        val type = OurSku.PRO_SKUS.singleOrNull { it.id == lastSku }?.type
        val window = if (type == Sku.Type.IAP) GRACE_PERIOD_IAP_MS else GRACE_PERIOD_MS
        log(TAG) { "graceWindowMs(): lastSku=$lastSku, type=$type -> ${window}ms" }
        return window
    }

    // Bounded + fail-soft DataStore read: on hang (timeout) or failure, degrade to "not recently pro"
    // (0L) so the grace fallback can never freeze or crash the entitlement mapping.
    private suspend fun readLastProStateAtSafe(): Long =
        readSafe("lastProStateAt") { billingCache.lastProStateAt.value() } ?: 0L

    private suspend fun <T> readSafe(name: String, read: suspend () -> T): T? = try {
        withTimeoutOrNull(CACHE_READ_TIMEOUT_MS) { read() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(TAG, WARN) { "$name read failed: ${e.asLog()}" }
        null
    }

    data class Info(
        private val gracePeriod: Boolean = false,
        private val billingData: BillingData?,
        override val error: Throwable? = null,
        override val isSettled: Boolean = false,
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

        override val isPro: Boolean = upgrades.isNotEmpty() || gracePeriod

        override val upgradedAt: Instant? = upgrades
            .maxByOrNull { it.purchase.purchaseTime }
            ?.let { Instant.ofEpochMilli(it.purchase.purchaseTime) }
    }


    companion object {
        private const val STORE_SITE = "https://play.google.com/store/apps/details?id=eu.darken.bluemusic"
        private const val UPGRADE_SITE = "https://play.google.com/store/apps/details?id=eu.darken.bluemusic"
        private const val BETA_SITE = "https://play.google.com/apps/testing/eu.darken.bluemusic"

        // Keep paying users Pro through transient empty/failed Play Billing responses. A permanent
        // one-time purchase should almost never be dropped on a hiccup, so it gets a long window; a
        // subscription legitimately lapses, so it keeps the short one. GRACE_PERIOD_MS is the
        // subscription/default window (also used when the last-owned SKU is unknown/legacy).
        val GRACE_PERIOD_MS = Duration.ofDays(7).toMillis()
        val GRACE_PERIOD_IAP_MS = Duration.ofDays(30).toMillis()
        private const val RESTORE_ON_OWNED_TIMEOUT_MS = 15_000L
        private const val REFRESH_TIMEOUT_MS = 30_000L

        // Bound on a single grace-cache DataStore read, so a hung/full-disk store can't stall the
        // reactive entitlement mapping.
        private const val CACHE_READ_TIMEOUT_MS = 2_000L

        // Bound on the grace-state stamp write, so a hung store can't stall the freshBillingData
        // bookkeeping collector.
        private const val CACHE_WRITE_TIMEOUT_MS = 5_000L

        // The first Play round-trip after sign-in can take >8s; give the UI a bounded wait before it
        // stops showing Loading, so a dead/slow Play can't pin it there forever.
        val SETTLE_FALLBACK_MS = Duration.ofSeconds(10).toMillis()

        val TAG: String = logTag("Upgrade", "Gplay", "Repo")

        // The SKU whose grace window applies when several are owned: the permanent one-time purchase
        // wins over a subscription (purchases are time-sorted, so firstOrNull alone isn't enough).
        // null when no known Pro SKU is owned.
        internal fun preferredProSku(upgrades: Collection<PurchasedSku>): Sku? =
            upgrades.firstOrNull { it.sku.type == Sku.Type.IAP }?.sku ?: upgrades.firstOrNull()?.sku
    }
}
