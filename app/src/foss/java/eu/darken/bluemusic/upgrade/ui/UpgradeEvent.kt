package eu.darken.bluemusic.upgrade.ui

sealed interface UpgradeEvent {
    data object SpendMoreTime : UpgradeEvent
}
