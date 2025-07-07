package eu.darken.bluemusic.common.navigation

import kotlinx.serialization.Serializable

sealed interface Nav : NavigationDestination {
    sealed interface Main : Nav {
        @Serializable
        data object Onboarding : Main {
            private fun readResolve(): Any = Onboarding
        }

        @Serializable
        data object Dashboard : Main {
            private fun readResolve(): Any = Dashboard
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

    sealed interface Settings
}