package eu.darken.bluemusic.common.navigation

import eu.darken.bluemusic.devices.core.DeviceAddr
import kotlinx.serialization.Serializable

sealed interface Nav : NavigationDestination {
    sealed interface Main : Nav {
        @Serializable
        data object Onboarding : Main {
            private fun readResolve(): Any = Onboarding
        }

        @Serializable
        data object ManageDevices : Main {
            private fun readResolve(): Any = ManageDevices
        }

        @Serializable
        data object DiscoverDevices : Main {
            private fun readResolve(): Any = ManageDevices
        }

        @Serializable
        data class DeviceConfig(val addr: DeviceAddr) : Main {
            private fun readResolve(): Any = ManageDevices
        }

        @Serializable
        data class AppSelection(val addr: DeviceAddr) : Main {
            private fun readResolve(): Any = ManageDevices
        }

        @Serializable
        data object SettingsIndex : Main {
            private fun readResolve(): Any = SettingsIndex
        }

        @Serializable
        data object Upgrade : Main {
            private fun readResolve(): Any = Upgrade
        }

    }

    sealed interface Settings : Nav {
        @Serializable
        data object General : Settings {
            private fun readResolve(): Any = General
        }

        @Serializable
        data object Devices : Settings {
            private fun readResolve(): Any = Devices
        }

        @Serializable
        data object Support : Settings {
            private fun readResolve(): Any = General
        }

        @Serializable
        data object Acks : Settings {
            private fun readResolve(): Any = General
        }

        @Serializable
        data object ContactSupport : Settings {
            private fun readResolve(): Any = ContactSupport
        }
    }
}