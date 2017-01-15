package eu.darken.bluemusic;

import dagger.Component;
import eu.darken.bluemusic.core.DeviceSourceModule;
import eu.darken.bluemusic.core.bluetooth.BluetoothSource;
import eu.darken.bluemusic.core.database.ManagedDeviceRepo;
import eu.darken.bluemusic.core.database.ManagedDeviceRepoModule;
import eu.darken.bluemusic.screens.volumes.VolumeManagerComponent;
import eu.darken.bluemusic.util.AndroidModule;
import eu.darken.bluemusic.util.dagger.ApplicationScope;


@ApplicationScope
@Component(modules = {
        AndroidModule.class,
        DeviceSourceModule.class,
        ManagedDeviceRepoModule.class
})
public interface AppComponent {
    BluetoothSource deviceSource();

    ManagedDeviceRepo knownDeviceRepository();

    VolumeManagerComponent volumeManagerComponent();
}
