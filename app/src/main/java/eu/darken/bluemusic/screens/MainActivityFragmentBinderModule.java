package eu.darken.bluemusic.screens;

import android.support.v4.app.Fragment;

import dagger.Binds;
import dagger.Module;
import dagger.android.AndroidInjector;
import dagger.android.support.FragmentKey;
import dagger.multibindings.IntoMap;
import eu.darken.bluemusic.screens.about.AboutComponent;
import eu.darken.bluemusic.screens.about.AboutFragment;
import eu.darken.bluemusic.screens.config.ConfigComponent;
import eu.darken.bluemusic.screens.config.ConfigFragment;
import eu.darken.bluemusic.screens.devices.DevicesComponent;
import eu.darken.bluemusic.screens.devices.DevicesFragment;
import eu.darken.bluemusic.screens.managed.ManagedDevicesComponent;
import eu.darken.bluemusic.screens.managed.ManagedDevicesFragment;
import eu.darken.bluemusic.screens.settings.SettingsComponent;
import eu.darken.bluemusic.screens.settings.SettingsFragment;

@Module(subcomponents = {
        ManagedDevicesComponent.class,
        DevicesComponent.class,
        SettingsComponent.class,
        AboutComponent.class,
        ConfigComponent.class
})
abstract class MainActivityFragmentBinderModule {

    @Binds
    @IntoMap
    @FragmentKey(ManagedDevicesFragment.class)
    abstract AndroidInjector.Factory<? extends Fragment> volumes(ManagedDevicesComponent.Builder impl);

    @Binds
    @IntoMap
    @FragmentKey(ConfigFragment.class)
    abstract AndroidInjector.Factory<? extends Fragment> config(ConfigComponent.Builder impl);

    @Binds
    @IntoMap
    @FragmentKey(DevicesFragment.class)
    abstract AndroidInjector.Factory<? extends Fragment> devices(DevicesComponent.Builder impl);

    @Binds
    @IntoMap
    @FragmentKey(SettingsFragment.class)
    abstract AndroidInjector.Factory<? extends Fragment> settings(SettingsComponent.Builder impl);

    @Binds
    @IntoMap
    @FragmentKey(AboutFragment.class)
    abstract AndroidInjector.Factory<? extends Fragment> about(AboutComponent.Builder impl);
}