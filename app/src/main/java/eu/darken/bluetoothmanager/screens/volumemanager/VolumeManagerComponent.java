package eu.darken.bluetoothmanager.screens.volumemanager;

import dagger.Component;
import eu.darken.bluetoothmanager.AppComponent;
import eu.darken.bluetoothmanager.util.dagger.FragmentScope;


@FragmentScope
@Component(modules = VolumeManagerModule.class, dependencies = AppComponent.class)
public interface VolumeManagerComponent {
    void inject(VolumeManagerFragment fragment);
}
