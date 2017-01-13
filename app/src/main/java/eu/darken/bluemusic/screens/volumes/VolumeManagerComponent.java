package eu.darken.bluemusic.screens.volumes;

import dagger.Component;
import eu.darken.bluemusic.AppComponent;
import eu.darken.bluemusic.util.dagger.FragmentScope;


@FragmentScope
@Component(modules = VolumeManagerModule.class, dependencies = AppComponent.class)
public interface VolumeManagerComponent {
    void inject(VolumeManagerFragment fragment);
}
