package eu.darken.bluemusic.util.iap

import com.android.billingclient.api.BillingClient.BillingResponseCode
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.error.UserFacingException

fun Exception.mapExceptionsUserFriendly(): Exception {
    return when (this) {
        is BillingClientException -> {
            when (result?.responseCode) {
                BillingResponseCode.USER_CANCELED -> UserFacingException(
                    "User canceled purchase flow",
                    R.string.error_iap_cancelled_message
                )
                BillingResponseCode.SERVICE_UNAVAILABLE -> UserFacingException(
                    "Network connection is down",
                    R.string.error_iap_unavailable_message
                )
                BillingResponseCode.BILLING_UNAVAILABLE -> UserFacingException(
                    "Billing API version is not supported for the type requested",
                    R.string.error_iap_unavailable_message
                )
                BillingResponseCode.ITEM_UNAVAILABLE -> UserFacingException(
                    "Requested product is not available for purchase",
                    R.string.error_iap_unavailable_message
                )
                BillingResponseCode.DEVELOPER_ERROR -> UserFacingException(
                    "Invalid arguments provided to the API",
                    R.string.error_generic_message
                )
                BillingResponseCode.ERROR -> UserFacingException(
                    "Fatal error during the API action",
                    R.string.error_generic_message
                )
                BillingResponseCode.ITEM_ALREADY_OWNED -> UserFacingException(
                    "Failure to purchase since item is already owned",
                    R.string.error_iap_owned_message
                )
                BillingResponseCode.ITEM_NOT_OWNED -> UserFacingException(
                    "Failure to consume since item is not owned",
                    R.string.error_generic_message
                )
                else -> UserFacingException(
                    "Unknown billing error: ${result?.debugMessage}",
                    R.string.error_generic_message
                )
            }
        }
        else -> this
    }
}