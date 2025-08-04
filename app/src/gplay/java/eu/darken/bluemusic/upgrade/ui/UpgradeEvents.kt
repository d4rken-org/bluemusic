package eu.darken.bluemusic.upgrade.ui

sealed class UpgradeEvents {
    data object RestoreFailed : UpgradeEvents()
}
