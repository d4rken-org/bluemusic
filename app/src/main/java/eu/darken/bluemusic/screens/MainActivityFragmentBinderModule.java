package eu.darken.bluemusic.screens;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;
import eu.darken.bluemusic.screens.volumes.VolumesComponent;
import eu.darken.bluemusic.screens.volumes.VolumesFragment;
import eu.darken.ommvplib.injection.fragment.FragmentComponentBuilder;
import eu.darken.ommvplib.injection.fragment.FragmentKey;

@Module(subcomponents = {VolumesComponent.class})
abstract class MainActivityFragmentBinderModule {

    @Binds
    @IntoMap
    @FragmentKey(VolumesFragment.class)
    abstract FragmentComponentBuilder countingFragment(VolumesComponent.Builder impl);
}