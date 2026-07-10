package eu.darken.bluemusic.upgrade.ui

import android.app.Activity
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    navCtrl: NavigationController,
    private val upgradeRepo: UpgradeRepoGplay,
) : ViewModel4(dispatcherProvider, logTag("Upgrade", "Screen", "VM"), navCtrl) {

    val events = SingleEventFlow<UpgradeEvents>()

    init {
        upgradeRepo.upgradeInfo
            .filter { it.isUpgraded }
            .take(1)
            .onEach { navUp() }
            .launchInViewModel()
    }

    val state = combine(
        flow {
            val data = withTimeoutOrNull(5000) {
                try {
                    upgradeRepo.querySkus(*OurSku.PRO_SKUS.filterIsInstance<Sku.Iap>().toTypedArray())
                } catch (e: Exception) {
                    errorEvents.emit(e)
                    null
                }
            }
            emit(data)
        },
        flow {
            val data = withTimeoutOrNull(5000) {
                try {
                    upgradeRepo.querySkus(*OurSku.PRO_SKUS.filterIsInstance<Sku.Subscription>().toTypedArray())
                } catch (e: Exception) {
                    errorEvents.emit(e)
                    null
                }
            }
            emit(data)
        },
        upgradeRepo.upgradeInfo,
        upgradeRepo.wasEverPro,
    ) { iap, sub, current, wasEverPro ->
        if (iap == null && sub == null) {
            errorEvents.emit(
                GplayServiceUnavailableException(RuntimeException("IAP and SUB data request timed out."))
            )
        }

        val iapOffer = iap?.singleOrNull { it.sku.id == OurSku.Iap.PRO_UPGRADE.id }?.details?.oneTimePurchaseOfferDetails
        val iapState = State.Iap(
            available = iapOffer != null && current.upgrades.none { it.sku == OurSku.Iap.PRO_UPGRADE },
            formattedPrice = iapOffer?.formattedPrice,
        )

        val subOffer = sub?.firstOrNull()?.details?.subscriptionOfferDetails?.singleOrNull { offer ->
            OurSku.Sub.PRO_UPGRADE.BASE_OFFER.matches(offer)
        }
        val subState = State.Sub(
            available = subOffer != null && current.upgrades.none { it.sku == OurSku.Sub.PRO_UPGRADE },
            formattedPrice = subOffer?.let { it.pricingPhases.pricingPhaseList.firstOrNull()?.formattedPrice },
        )

        val trialOffer = sub?.firstOrNull()?.details?.subscriptionOfferDetails?.any { offer ->
            OurSku.Sub.PRO_UPGRADE.TRIAL_OFFER.matches(offer)
        }
        val trialState = State.Trial(
            available = trialOffer == true,
            formattedPrice = subState.formattedPrice,
        )

        State(
            iapState = iapState,
            subState = subState,
            trialState = trialState,
            // Hidden while a grace period or an actual purchase keeps the user Pro.
            wasPreviouslyPro = wasEverPro && !current.isUpgraded,
        )
    }.asStateFlow()

    data class State(
        val iapState: Iap,
        val subState: Sub,
        val trialState: Trial,
        val wasPreviouslyPro: Boolean = false,
    ) {

        class Iap(
            val available: Boolean,
            val formattedPrice: String?,
        )

        data class Sub(
            val available: Boolean,
            val formattedPrice: String?,
        )

        data class Trial(
            val available: Boolean,
            val formattedPrice: String?,
        )
    }

    fun onGoIap(activity: Activity) = launch {
        log(tag) { "onGoIap($activity)" }
        upgradeRepo.launchBillingFlow(activity, OurSku.Iap.PRO_UPGRADE, null)
    }

    fun onGoSubscription(activity: Activity) = launch {
        log(tag) { "onGoSubscription($activity)" }
        upgradeRepo.launchBillingFlow(activity, OurSku.Sub.PRO_UPGRADE, OurSku.Sub.PRO_UPGRADE.BASE_OFFER)
    }

    fun onGoSubscriptionTrial(activity: Activity) = launch {
        log(tag) { "onGoSubscriptionTrial($activity)" }
        upgradeRepo.launchBillingFlow(activity, OurSku.Sub.PRO_UPGRADE, OurSku.Sub.PRO_UPGRADE.TRIAL_OFFER)
    }

    fun restorePurchase() = launch {
        log(tag) { "restorePurchase()" }

        try {
            val restored = withTimeoutOrNull(RESTORE_TIMEOUT_MS) { upgradeRepo.restorePurchaseNow() }
            when {
                restored == null -> {
                    // Play never answered in time; the restore-failed dialog already suggests waiting /
                    // clearing the Play cache, which fits a timeout too.
                    log(tag, WARN) { "Restore purchase timed out" }
                    events.tryEmit(UpgradeEvents.RestoreFailed)
                }

                restored.isUpgraded -> log(tag, INFO) { "Restored purchase :))" }

                else -> {
                    log(tag, WARN) { "Restore purchase failed" }
                    events.tryEmit(UpgradeEvents.RestoreFailed)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Play/billing error (e.g. service unavailable): surface the proper error dialog instead
            // of the generic "restore failed" message, so the user can tell the two cases apart.
            log(tag, WARN) { "Restore purchase errored: ${e.asLog()}" }
            errorEvents.tryEmit(e)
        }
    }

    companion object {
        private const val RESTORE_TIMEOUT_MS = 15_000L
    }
}