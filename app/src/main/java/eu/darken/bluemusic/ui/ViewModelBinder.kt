package eu.darken.bluemusic.ui

import androidx.lifecycle.ViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import eu.darken.bluemusic.common.dagger.ViewModelKey
import eu.darken.bluemusic.ui.about.AboutViewModel
import eu.darken.bluemusic.ui.advanced.AdvancedViewModel
import eu.darken.bluemusic.ui.config.ConfigViewModel
import eu.darken.bluemusic.ui.discover.DiscoverViewModel
import eu.darken.bluemusic.ui.intro.IntroViewModel
import eu.darken.bluemusic.ui.manageddevices.ManagedDevicesViewModel
import eu.darken.bluemusic.ui.settings.SettingsViewModel

@Module
abstract class ViewModelBinder {
    
    @Binds
    @IntoMap
    @ViewModelKey(ManagedDevicesViewModel::class)
    abstract fun bindManagedDevicesViewModel(viewModel: ManagedDevicesViewModel): ViewModel
    
    @Binds
    @IntoMap
    @ViewModelKey(ConfigViewModel::class)
    abstract fun bindConfigViewModel(viewModel: ConfigViewModel): ViewModel
    
    @Binds
    @IntoMap
    @ViewModelKey(DiscoverViewModel::class)
    abstract fun bindDiscoverViewModel(viewModel: DiscoverViewModel): ViewModel
    
    @Binds
    @IntoMap
    @ViewModelKey(IntroViewModel::class)
    abstract fun bindIntroViewModel(viewModel: IntroViewModel): ViewModel
    
    @Binds
    @IntoMap
    @ViewModelKey(SettingsViewModel::class)
    abstract fun bindSettingsViewModel(viewModel: SettingsViewModel): ViewModel
    
    @Binds
    @IntoMap
    @ViewModelKey(AdvancedViewModel::class)
    abstract fun bindAdvancedViewModel(viewModel: AdvancedViewModel): ViewModel
    
    @Binds
    @IntoMap
    @ViewModelKey(AboutViewModel::class)
    abstract fun bindAboutViewModel(viewModel: AboutViewModel): ViewModel
}