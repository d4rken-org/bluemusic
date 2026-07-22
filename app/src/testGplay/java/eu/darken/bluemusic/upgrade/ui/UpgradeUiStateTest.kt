package eu.darken.bluemusic.upgrade.ui

import com.android.billingclient.api.ProductDetails
import eu.darken.bluemusic.upgrade.core.OurSku
import eu.darken.bluemusic.upgrade.core.billing.SkuDetails
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class UpgradeUiStateTest : BaseTest() {

    private fun iapSku(price: String = "$4.99"): SkuDetails {
        val details = mockk<ProductDetails> {
            every { oneTimePurchaseOfferDetails } returns mockk {
                every { formattedPrice } returns price
            }
            every { subscriptionOfferDetails } returns null
        }
        return SkuDetails(OurSku.Iap.PRO_UPGRADE, details)
    }

    @Test fun `ownsAnything reflects iap or subscription`() {
        Ownership().ownsAnything shouldBe false
        Ownership(hasIap = true).ownsAnything shouldBe true
        Ownership(subscription = SubscriptionOwnership(isAutoRenewing = true)).ownsAnything shouldBe true
    }

    @Test fun `no offers yields unavailable, disabled actions`() {
        val loaded = toLoadedState(
            iap = null,
            sub = null,
            ownership = Ownership(),
            grace = null,
            wasPreviouslyPro = false,
            settled = true,
            restoreInProgress = false,
            verificationInProgress = false,
        )
        loaded.subscriptionAction shouldBe SubscriptionAction.UNAVAILABLE
        loaded.subscriptionEnabled shouldBe false
        loaded.iapEnabled shouldBe false
        loaded.iapPrice shouldBe null
    }

    @Test fun `a one-time offer enables the iap action and exposes its price`() {
        val loaded = toLoadedState(
            iap = iapSku(),
            sub = null,
            ownership = Ownership(),
            grace = null,
            wasPreviouslyPro = false,
            settled = true,
            restoreInProgress = false,
            verificationInProgress = false,
        )
        loaded.iapEnabled shouldBe true
        loaded.iapPrice shouldBe "$4.99"
    }

    @Test fun `an unsettled billing layer disables the iap action`() {
        val loaded = toLoadedState(
            iap = iapSku(),
            sub = null,
            ownership = Ownership(),
            grace = null,
            wasPreviouslyPro = false,
            settled = false,
            restoreInProgress = false,
            verificationInProgress = false,
        )
        loaded.iapEnabled shouldBe false
        // Price is still surfaced even though the button is disabled.
        loaded.iapPrice shouldBe "$4.99"
    }

    @Test fun `offer availability is independent of settle and busy state`() {
        // Before the billing layer settles, an existing offer must still be reported as AVAILABLE
        // (so the real button is shown, merely disabled) — never hidden behind the generic fallback.
        val loaded = toLoadedState(
            iap = iapSku(),
            sub = null,
            ownership = Ownership(),
            grace = null,
            wasPreviouslyPro = false,
            settled = false,
            restoreInProgress = true,
            verificationInProgress = false,
        )
        loaded.iapAvailable shouldBe true
        loaded.iapEnabled shouldBe false
    }

    @Test fun `owning the iap disables the iap action`() {
        val loaded = toLoadedState(
            iap = iapSku(),
            sub = null,
            ownership = Ownership(hasIap = true),
            grace = null,
            wasPreviouslyPro = false,
            settled = true,
            restoreInProgress = false,
            verificationInProgress = false,
        )
        loaded.iapEnabled shouldBe false
    }
}
