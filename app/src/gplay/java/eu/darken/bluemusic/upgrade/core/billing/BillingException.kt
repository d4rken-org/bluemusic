package eu.darken.bluemusic.upgrade.core.billing

import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.ca.toCaString
import eu.darken.bluemusic.common.error.HasLocalizedError
import eu.darken.bluemusic.common.error.LocalizedError

open class BillingException(
    override val message: String? = null,
    override val cause: Throwable? = null,
) : Exception(), HasLocalizedError {

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.upgrades_gplay_billing_error_label.toCaString(),
        description = R.string.upgrades_gplay_billing_error_description.toCaString(message.orEmpty())
    )
}