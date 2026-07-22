package eu.darken.bluemusic.upgrade.ui

import eu.darken.bluemusic.upgrade.core.OurSku
import eu.darken.bluemusic.upgrade.core.UpgradeRepoGplay
import eu.darken.bluemusic.upgrade.core.billing.SkuDetails

sealed interface UpgradeUiState {

    data object Loading : UpgradeUiState

    data class Loaded(
        val subscriptionAction: SubscriptionAction,
        val subscriptionEnabled: Boolean,
        val subscriptionPrice: String?,
        val iapEnabled: Boolean,
        val iapPrice: String?,
        val ownership: Ownership = Ownership(),
        val grace: GraceHint? = null,
        val wasPreviouslyPro: Boolean = false,
        val restoreInProgress: Boolean = false,
        val verificationInProgress: Boolean = false,
    ) : UpgradeUiState {

        // Whether an eligible, not-yet-owned offer EXISTS — independent of transient busy/settle state.
        // Drives which acquisition buttons are shown; the busy-gated *Enabled flags drive whether they
        // are clickable. Keeping these separate avoids the real buttons flickering out to a generic
        // fallback button during the unsettled window or while a restore/verify runs.
        val subscriptionAvailable: Boolean
            get() = subscriptionAction != SubscriptionAction.UNAVAILABLE && ownership.subscription == null
        val iapAvailable: Boolean
            get() = iapPrice != null && !ownership.hasIap
    }
}

// Non-null only while the user is Pro purely via the local grace window (no owned purchase). Stage 1
// ("confirming your purchase", quiet) becomes stage 2 (diagnostics + restore + offers) once the
// entitlement has been unconfirmed past the 24h boundary.
data class GraceHint(val showDiagnostics: Boolean)

data class Ownership(
    val hasIap: Boolean = false,
    val subscription: SubscriptionOwnership? = null,
) {
    val ownsAnything: Boolean get() = hasIap || subscription != null
}

data class SubscriptionOwnership(val isAutoRenewing: Boolean)

enum class SubscriptionAction { TRIAL, STANDARD, UNAVAILABLE }

// Conservative: if ANY record for the subscription SKU still claims auto-renew, treat the whole
// subscription as renewing. This can only under-offer the one-time switch (never wrongly enable it);
// the actual buy tap re-verifies against a fresh Play query anyway.
fun UpgradeRepoGplay.Info.toOwnership(): Ownership = Ownership(
    hasIap = upgrades.any {
        it.sku == OurSku.Iap.PRO_UPGRADE || it.sku == OurSku.Iap.PRO_UPGRADE_LEGACY
    },
    subscription = upgrades
        .filter { it.sku == OurSku.Sub.PRO_UPGRADE }
        .takeIf { it.isNotEmpty() }
        ?.let { subs -> SubscriptionOwnership(isAutoRenewing = subs.any { it.purchase.isAutoRenewing }) },
)

fun toLoadedState(
    iap: SkuDetails?,
    sub: SkuDetails?,
    ownership: Ownership,
    grace: GraceHint?,
    wasPreviouslyPro: Boolean,
    settled: Boolean,
    restoreInProgress: Boolean,
    verificationInProgress: Boolean,
): UpgradeUiState.Loaded {
    val iapOffer = iap?.details?.oneTimePurchaseOfferDetails
    val subOffer = sub?.details?.subscriptionOfferDetails?.singleOrNull {
        OurSku.Sub.PRO_UPGRADE.BASE_OFFER.matches(it)
    }
    val subOfferTrial = sub?.details?.subscriptionOfferDetails?.singleOrNull {
        OurSku.Sub.PRO_UPGRADE.TRIAL_OFFER.matches(it)
    }
    val actionsFree = settled && !restoreInProgress && !verificationInProgress
    return UpgradeUiState.Loaded(
        subscriptionAction = when {
            subOfferTrial != null -> SubscriptionAction.TRIAL
            subOffer != null -> SubscriptionAction.STANDARD
            else -> SubscriptionAction.UNAVAILABLE
        },
        subscriptionEnabled = (subOffer != null || subOfferTrial != null) &&
            ownership.subscription == null && actionsFree,
        subscriptionPrice = subOffer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice,
        iapEnabled = iapOffer != null && !ownership.hasIap && actionsFree,
        iapPrice = iapOffer?.formattedPrice,
        ownership = ownership,
        grace = grace,
        wasPreviouslyPro = wasPreviouslyPro,
        restoreInProgress = restoreInProgress,
        verificationInProgress = verificationInProgress,
    )
}
