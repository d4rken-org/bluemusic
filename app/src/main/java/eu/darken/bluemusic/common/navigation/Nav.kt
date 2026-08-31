package eu.darken.bluemusic.common.navigation

import eu.darken.bluemusic.devices.core.DeviceAddr
import kotlinx.serialization.Serializable

sealed interface Nav : NavigationDestination {
    sealed interface Main : Nav {
        @Serializable
        data object Onboarding : Main

        @Serializable
        data object ManageDevices : Main

        @Serializable
        data object DiscoverDevices : Main

        @Serializable
        data class DeviceConfig(val addr: DeviceAddr) : Main

        @Serializable
        data class AppSelection(val addr: DeviceAddr) : Main

        @Serializable
        data class DeviceEq(val addr: DeviceAddr) : Main

        @Serializable
        data class DeviceVolumeLimit(val addr: DeviceAddr) : Main

        @Serializable
        data object SettingsIndex : Main

        @Serializable
        data class Upgrade(val manage: Boolean = false) : Main

        @Serializable
        data object EqSessions : Main

    }

    sealed interface Settings : Nav {
        @Serializable
        data object General : Settings

        @Serializable
        data object Devices : Settings

        @Serializable
        data object Support : Settings

        @Serializable
        data object Acks : Settings

        @Serializable
        data object BackupRestore : Settings

        @Serializable
        data object ContactSupport : Settings
    }
}
