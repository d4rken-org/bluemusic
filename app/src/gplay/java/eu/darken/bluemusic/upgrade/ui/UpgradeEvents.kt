package eu.darken.bluemusic.upgrade.ui

sealed class UpgradeEvents {
    data object RestoreSucceeded : UpgradeEvents()
    data object RestoreFailed : UpgradeEvents()

    // The fail-closed IAP switch gate blocked a purchase because a subscription is still set to renew.
    data object SubscriptionStillRenewing : UpgradeEvents()

    // The gate couldn't confirm the subscription state (timeout / Play error) — fail closed.
    data object SubscriptionCheckFailed : UpgradeEvents()
}
