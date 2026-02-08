package eu.darken.bluemusic.main.ui.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.navigation.NavigationEntry
import eu.darken.bluemusic.main.ui.settings.acknowledgements.AcknowledgementsScreenHost
import eu.darken.bluemusic.main.ui.settings.general.GeneralSettingsScreenHost
import eu.darken.bluemusic.main.ui.settings.support.SupportScreenHost
import javax.inject.Inject

class SettingsNavigation @Inject constructor() : NavigationEntry {
    override fun EntryProviderScope<NavKey>.setup() {
        entry<Nav.Main.SettingsIndex> {
            SettingsIndexScreenHost()
        }
        entry<Nav.Settings.General> {
            GeneralSettingsScreenHost()
        }
        entry<Nav.Settings.Support> {
            SupportScreenHost()
        }
        entry<Nav.Settings.Acks> {
            AcknowledgementsScreenHost()
        }

    }

    @Suppress("unused")
    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: SettingsNavigation): NavigationEntry
    }
}
