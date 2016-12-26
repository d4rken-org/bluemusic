package eu.darken.bluetoothmanager.screens.newdevice.volumemanager;

import dagger.Component;
import eu.darken.bluetoothmanager.AppComponent;
import eu.darken.bluetoothmanager.util.dagger.ActivityScope;


@ActivityScope
@Component(modules = NewDeviceModule.class, dependencies = AppComponent.class)
public interface NewDeviceComponent {
    void inject(NewDeviceActivity activity);
}
