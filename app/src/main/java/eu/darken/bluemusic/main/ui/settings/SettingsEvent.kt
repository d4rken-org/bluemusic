package eu.darken.bluemusic.main.ui.settings

sealed interface SettingsEvent {
    data object VersionCopied : SettingsEvent
}