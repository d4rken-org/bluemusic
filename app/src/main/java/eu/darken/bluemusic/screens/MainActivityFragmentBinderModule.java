package eu.darken.bluemusic.screens;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;
import eu.darken.bluemusic.screens.about.AboutComponent;
import eu.darken.bluemusic.screens.about.AboutFragment;
import eu.darken.bluemusic.screens.settings.SettingsComponent;
import eu.darken.bluemusic.screens.settings.SettingsFragment;
import eu.darken.bluemusic.screens.volumes.VolumesComponent;
import eu.darken.bluemusic.screens.volumes.VolumesFragment;
import eu.darken.ommvplib.injection.fragment.FragmentComponentBuilder;
import eu.darken.ommvplib.injection.fragment.FragmentKey;

@Module(subcomponents = {VolumesComponent.class, SettingsComponent.class, AboutComponent.class})
abstract class MainActivityFragmentBinderModule {

    @Binds
    @IntoMap
    @FragmentKey(VolumesFragment.class)
    abstract FragmentComponentBuilder volumes(VolumesComponent.Builder impl);

    @Binds
    @IntoMap
    @FragmentKey(SettingsFragment.class)
    abstract FragmentComponentBuilder settings(SettingsComponent.Builder impl);

    @Binds
    @IntoMap
    @FragmentKey(AboutFragment.class)
    abstract FragmentComponentBuilder about(AboutComponent.Builder impl);
}