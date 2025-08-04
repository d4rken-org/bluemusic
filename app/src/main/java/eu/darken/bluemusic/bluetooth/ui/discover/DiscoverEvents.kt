package eu.darken.bluemusic.bluetooth.ui.discover

sealed interface DiscoverEvent {
    data object RequiresUpgrade : DiscoverEvent
}