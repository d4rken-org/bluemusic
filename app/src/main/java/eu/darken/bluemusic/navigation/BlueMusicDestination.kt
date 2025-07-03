package eu.darken.bluemusic.navigation

sealed class BlueMusicDestination(val route: String) {
    data object ManagedDevices : BlueMusicDestination("managed_devices")
    data object Onboarding : BlueMusicDestination("onboarding")
    data object Discover : BlueMusicDestination("discover")
    data object Settings : BlueMusicDestination("settings")
    data object AdvancedSettings : BlueMusicDestination("advanced_settings")
    data object About : BlueMusicDestination("about")
    
    data class DeviceConfig(val deviceAddress: String) : BlueMusicDestination("device_config/{address}") {
        companion object {
            const val ROUTE = "device_config/{address}"
            const val ADDRESS_ARG = "address"
            
            fun createRoute(address: String) = "device_config/$address"
        }
    }
}