package eu.darken.bluemusic.upgrade.core.billing

import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.ca.toCaString
import eu.darken.bluemusic.common.error.HasLocalizedError
import eu.darken.bluemusic.common.error.LocalizedError

class ItemAlreadyOwnedBillingException(cause: Throwable) :
    BillingException("Item is already owned.", cause), HasLocalizedError {

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.upgrades_gplay_already_owned_error_title.toCaString(),
        description = R.string.upgrades_gplay_already_owned_error_description.toCaString(),
    )
}
