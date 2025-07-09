package eu.darken.bluemusic.devices.ui.manage

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.navigation.NavigationEntry
import javax.inject.Inject

class DevicesNavigation @Inject constructor() : NavigationEntry {
    override fun EntryProviderBuilder<NavKey>.setup() {
        entry<Nav.Main.ManageDevices> {
            DevicesScreenHost()
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: DevicesNavigation): NavigationEntry
    }
}
