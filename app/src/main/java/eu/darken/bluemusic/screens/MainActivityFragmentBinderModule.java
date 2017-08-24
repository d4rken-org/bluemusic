package eu.darken.bluemusic.screens;

import android.support.v4.app.Fragment;

import dagger.Binds;
import dagger.Module;
import dagger.android.AndroidInjector;
import dagger.android.support.FragmentKey;
import dagger.multibindings.IntoMap;
import eu.darken.bluemusic.screens.about.AboutComponent;
import eu.darken.bluemusic.screens.about.AboutFragment;
import eu.darken.bluemusic.screens.devices.DevicesFragment;
import eu.darken.bluemusic.screens.settings.SettingsComponent;
import eu.darken.bluemusic.screens.settings.SettingsFragment;
import eu.darken.bluemusic.screens.volumes.VolumesComponent;
import eu.darken.bluemusic.screens.volumes.VolumesFragment;

@Module(subcomponents = {VolumesComponent.class, SettingsComponent.class, AboutComponent.class})
abstract class MainActivityFragmentBinderModule {

    @Binds
    @IntoMap
    @FragmentKey(VolumesFragment.class)
    abstract AndroidInjector.Factory<? extends Fragment> volumes(VolumesComponent.Builder impl);

    @Binds
    @IntoMap
    @FragmentKey(DevicesFragment.class)
    abstract AndroidInjector.Factory<? extends Fragment> devices(VolumesComponent.Builder impl);

    @Binds
    @IntoMap
    @FragmentKey(SettingsFragment.class)
    abstract AndroidInjector.Factory<? extends Fragment> settings(SettingsComponent.Builder impl);

    @Binds
    @IntoMap
    @FragmentKey(AboutFragment.class)
    abstract AndroidInjector.Factory<? extends Fragment> about(AboutComponent.Builder impl);
}