package eu.darken.bluemusic.devices.ui.dashboard

sealed interface DashboardEvent {
    data class RequestPermission(val permission: String) : DashboardEvent
}