package eu.darken.bluetoothmanager;

import dagger.Component;
import eu.darken.bluetoothmanager.core.manager.BluetoothSource;
import eu.darken.bluetoothmanager.core.DeviceSourceModule;
import eu.darken.bluetoothmanager.core.device.DeviceRepoModule;
import eu.darken.bluetoothmanager.core.device.ManagedDeviceRepo;
import eu.darken.bluetoothmanager.util.dagger.ApplicationScope;
import eu.darken.bluetoothmanager.util.AndroidModule;


@ApplicationScope
@Component(modules = {
        AndroidModule.class,
        DeviceSourceModule.class,
        DeviceRepoModule.class
})
public interface AppComponent {
    BluetoothSource deviceSource();

    ManagedDeviceRepo knownDeviceRepository();
}
