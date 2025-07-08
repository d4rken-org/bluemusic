package eu.darken.bluemusic.devices.ui.manage

sealed interface DevicesEvent {
    data class RequestPermission(val permission: String) : DevicesEvent
}