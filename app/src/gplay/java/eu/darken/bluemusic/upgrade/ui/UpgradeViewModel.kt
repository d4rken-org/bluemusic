package eu.darken.bluemusic.upgrade.ui

import android.app.Activity
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.WebpageTool
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.SingleEventFlow
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.upgrade.core.OurSku
import eu.darken.bluemusic.upgrade.core.UpgradeRepoGplay
import eu.darken.bluemusic.upgrade.core.billing.GplayServiceUnavailableException
import eu.darken.bluemusic.upgrade.core.billing.Sku
import eu.darken.bluemusic.upgrade.core.billing.SkuDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration

@HiltViewModel(assistedFactory = UpgradeViewModel.Factory::class)
class UpgradeViewModel @AssistedInject constructor(
    @Assisted private val manage: Boolean,
    dispatcherProvider: DispatcherProvider,
    navCtrl: NavigationController,
    private val upgradeRepo: UpgradeRepoGplay,
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatcherProvider, logTag("Upgrade", "Screen", "VM"), navCtrl) {

    val events = SingleEventFlow<UpgradeEvents>()

    private val restoring = MutableStateFlow(false)
    private val verifying = MutableStateFlow(false)

    // Manual restore OR the repo's invisible already-owned auto-restore: either pauses entitlement actions.
    private val restoreBusy = combine(restoring, upgradeRepo.autoRestoreBusy) { manual, auto ->
        manual || auto
    }

    init {
        // Only the acquisition entry (manage=false, opened from an upsell) auto-closes the instant the
        // user becomes Pro. The manage entry (settings status row, manage=true) stays open so an owner
        // can see their status and switch subscription -> one-time purchase.
        if (!manage) {
            upgradeRepo.upgradeInfo
                .filter { it.isUpgraded }
                .take(1)
                .onEach { navUp() }
                .launchInViewModel()
        }
    }

    private sealed interface SkuQueries {
        data object Loading : SkuQueries
        data class Done(val iap: Collection<SkuDetails>?, val sub: Collection<SkuDetails>?) : SkuQueries
    }

    // Bumped by onRetry() so a user stuck on the Unavailable state can re-run the price queries
    // without leaving and reopening the screen.
    private val skuRetry = MutableStateFlow(0)

    private val skuQueries = skuRetry.flatMapLatest {
        flow {
            emit(SkuQueries.Loading)
            val result = coroutineScope {
                val iapJob = async { queryOrNull(OurSku.PRO_SKUS.filterIsInstance<Sku.Iap>()) }
                val subJob = async { queryOrNull(OurSku.PRO_SKUS.filterIsInstance<Sku.Subscription>()) }
                SkuQueries.Done(iap = iapJob.await(), sub = subJob.await())
            }
            emit(result)
        }
    }

    // A failed/timed-out price query returns null; the combine surfaces it as the Unavailable state
    // (with Retry) rather than an error dialog, so the failure has a recovery path on screen.
    private suspend fun queryOrNull(skus: List<Sku>): Collection<SkuDetails>? =
        withTimeoutOrNull(SKU_QUERY_TIMEOUT_MS) {
            try {
                upgradeRepo.querySkus(*skus.toTypedArray())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(tag, WARN) { "SKU query failed: ${e.asLog()}" }
                null
            }
        }

    // Emits once immediately, then again the moment the entitlement crosses the 24h diagnostics
    // boundary — every other combined source is distinctUntilChanged and would otherwise never re-fire
    // while the screen sits open through the transition. flatMapLatest re-arms whenever the
    // last-confirmed timestamp changes; no-ops (single emission) when there's nothing to wait for.
    private val graceTick = upgradeRepo.lastProStateAt.flatMapLatest { lastProAt ->
        if (lastProAt <= 0L) return@flatMapLatest flowOf(Unit)
        val remaining = GRACE_DIAGNOSTICS_AFTER_MS - (System.currentTimeMillis() - lastProAt)
        if (remaining <= 0L) {
            flowOf(Unit)
        } else {
            flow {
                emit(Unit)
                delay(remaining)
                emit(Unit)
            }
        }
    }

    private data class Signals(
        val settled: Boolean,
        val wasEverPro: Boolean,
        val lastProStateAt: Long,
    )

    private val signals = combine(
        upgradeRepo.isSettled,
        upgradeRepo.wasEverPro,
        upgradeRepo.lastProStateAt,
        graceTick,
    ) { settled, wasEverPro, lastProAt, _ -> Signals(settled, wasEverPro, lastProAt) }

    val state = combine(
        skuQueries,
        upgradeRepo.upgradeInfo,
        signals,
        restoreBusy,
        verifying,
    ) { queries, current, sig, isRestoring, isVerifying ->
        val ownership = current.toOwnership()

        // Pro without any owned purchase == grace. Stage 1 shows immediately; the diagnostics stage
        // appears only once the entitlement has been unconfirmed past the 24h boundary (derived from
        // the last-confirmed timestamp, which can't get stuck during a Play outage).
        val grace = if (current.isUpgraded && !ownership.ownsAnything) {
            GraceHint(
                showDiagnostics = sig.lastProStateAt > 0L &&
                    (System.currentTimeMillis() - sig.lastProStateAt) >= GRACE_DIAGNOSTICS_AFTER_MS,
            )
        } else {
            null
        }

        // Owners and grace users don't need prices, so they must never be blocked on (or shown an
        // error for) a failed price query.
        val priceIndependent = ownership.ownsAnything || grace != null

        val done = queries as? SkuQueries.Done
        if (done == null && !priceIndependent) return@combine UpgradeUiState.Loading

        val iap = done?.iap
        val sub = done?.sub

        // Both price queries failed for a user who needs prices -> Unavailable (error card + Retry),
        // never a dead end. Owners/grace are priceIndependent and fall through to their own content.
        if (done != null && iap == null && sub == null && !priceIndependent) {
            return@combine UpgradeUiState.Unavailable(
                GplayServiceUnavailableException(RuntimeException("IAP and SUB data request timed out.")),
            )
        }

        toLoadedState(
            iap = iap?.singleOrNull { it.sku == OurSku.Iap.PRO_UPGRADE },
            sub = sub?.firstOrNull(),
            ownership = ownership,
            grace = grace,
            // Hidden while a grace period or an actual purchase keeps the user Pro.
            wasPreviouslyPro = sig.wasEverPro && !current.isUpgraded,
            settled = sig.settled,
            restoreInProgress = isRestoring,
            verificationInProgress = isVerifying,
        )
    }.asStateFlow()

    fun onGoIap(activity: Activity) {
        log(tag) { "onGoIap($activity)" }
        launch {
            // Mutually exclusive with a restore (manual OR the silent already-owned auto-restore): a
            // purchase verification and a restore both hit Play, and running them together can stack
            // result dialogs or let a buy race the already-owned recovery.
            if (restoring.value || upgradeRepo.autoRestoreBusy.first()) {
                log(tag) { "onGoIap() ignored, a restore is in progress" }
                return@launch
            }
            // Single-flight: repeated taps while the fail-closed check runs must not stack.
            if (!verifying.compareAndSet(expect = false, update = true)) {
                log(tag) { "onGoIap() ignored, verification already in progress" }
                return@launch
            }
            try {
                // Fail-closed double-billing gate. A fresh SUBS-only Play read on every IAP tap: a
                // timeout AND any exception both fold to the fail-closed path — we never launch the
                // one-time purchase while a subscription is (or might still be) renewing.
                val subscriptions = try {
                    withTimeoutOrNull(VERIFY_TIMEOUT_MS) { upgradeRepo.queryCurrentSubscriptions() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(tag, WARN) { "Subscription check failed: ${e.asLog()}" }
                    null
                }
                when {
                    subscriptions == null -> events.tryEmit(UpgradeEvents.SubscriptionCheckFailed)
                    subscriptions.any { it.isAutoRenewing } -> events.tryEmit(UpgradeEvents.SubscriptionStillRenewing)
                    else -> upgradeRepo.launchBillingFlow(
                        activity,
                        OurSku.Iap.PRO_UPGRADE,
                        null,
                        onError = errorEvents::tryEmit,
                    )
                }
            } finally {
                verifying.value = false
            }
        }
    }

    fun onGoSubscription(activity: Activity) {
        log(tag) { "onGoSubscription($activity)" }
        upgradeRepo.launchBillingFlow(
            activity,
            OurSku.Sub.PRO_UPGRADE,
            OurSku.Sub.PRO_UPGRADE.BASE_OFFER,
            onError = errorEvents::tryEmit,
        )
    }

    fun onGoSubscriptionTrial(activity: Activity) {
        log(tag) { "onGoSubscriptionTrial($activity)" }
        upgradeRepo.launchBillingFlow(
            activity,
            OurSku.Sub.PRO_UPGRADE,
            OurSku.Sub.PRO_UPGRADE.TRIAL_OFFER,
            onError = errorEvents::tryEmit,
        )
    }

    fun onManageSubscription() {
        log(tag) { "onManageSubscription()" }
        webpageTool.open(PLAY_SUBSCRIPTION_SITE)
    }

    fun onRetry() {
        log(tag) { "onRetry()" }
        skuRetry.update { it + 1 }
    }

    fun restorePurchase() = launch {
        // Mutually exclusive with the IAP verification gate (see onGoIap) and with the silent
        // already-owned auto-restore: all hit Play and stacking them can duplicate result dialogs.
        if (verifying.value || upgradeRepo.autoRestoreBusy.first()) {
            log(tag) { "restorePurchase() ignored, a verification or auto-restore is in progress" }
            return@launch
        }
        // Single-flight: repeated taps while a restore runs must not stack concurrent restores and
        // duplicate result dialogs.
        if (!restoring.compareAndSet(expect = false, update = true)) {
            log(tag) { "restorePurchase() ignored, already in progress" }
            return@launch
        }
        log(tag) { "restorePurchase()" }

        try {
            val restored = coroutineScope {
                // A warm billing cache answers instantly; pad to a minimum visible duration so the
                // spinner doesn't flash for a single frame and leave the user unsure anything happened.
                val minVisible = async { delay(RESTORE_MIN_VISIBLE_MS) }
                val result = withTimeoutOrNull(RESTORE_TIMEOUT_MS) { upgradeRepo.restorePurchaseNow() }
                minVisible.await()
                result
            }
            when {
                restored == null -> {
                    log(tag, WARN) { "Restore purchase timed out" }
                    events.tryEmit(UpgradeEvents.RestoreFailed)
                }

                restored.upgrades.isNotEmpty() -> {
                    log(tag, INFO) { "Restored purchase :))" }
                    events.tryEmit(UpgradeEvents.RestoreSucceeded)
                }

                else -> {
                    // Grace-only (no owned purchase) is not a real restore success.
                    log(tag, WARN) { "Restore purchase failed (grace-only or not owned)" }
                    events.tryEmit(UpgradeEvents.RestoreFailed)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Play/billing error (e.g. service unavailable): surface the proper error dialog instead of
            // the generic "restore failed" message.
            log(tag, WARN) { "Restore purchase errored: ${e.asLog()}" }
            errorEvents.tryEmit(e)
        } finally {
            restoring.value = false
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(manage: Boolean): UpgradeViewModel
    }

    companion object {
        private const val RESTORE_TIMEOUT_MS = 15_000L
        private const val RESTORE_MIN_VISIBLE_MS = 1_500L

        // Fail-closed gate budget. The billing connection is warm by the time the user taps (SKU/price
        // queries already ran), so this normally resolves in well under a second.
        private const val VERIFY_TIMEOUT_MS = 10_000L

        // The very first billing query after Play sign-in can take >8s while Play warms up.
        private const val SKU_QUERY_TIMEOUT_MS = 15_000L

        private val GRACE_DIAGNOSTICS_AFTER_MS = Duration.ofHours(24).toMillis()

        private val PLAY_SUBSCRIPTION_SITE =
            "https://play.google.com/store/account/subscriptions" +
                "?sku=${OurSku.Sub.PRO_UPGRADE.id}&package=eu.darken.bluemusic"
    }
}
