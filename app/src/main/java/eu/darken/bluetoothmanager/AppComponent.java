package eu.darken.bluetoothmanager;

import dagger.Component;
import eu.darken.bluetoothmanager.backend.live.DeviceSource;
import eu.darken.bluetoothmanager.backend.DeviceSourceModule;
import eu.darken.bluetoothmanager.backend.known.KnownDeviceRepository;
import eu.darken.bluetoothmanager.backend.known.KnownDeviceRepositoryModule;
import eu.darken.bluetoothmanager.util.dagger.ApplicationScope;
import eu.darken.bluetoothmanager.util.AndroidModule;


@ApplicationScope
@Component(modules = {
        AndroidModule.class,
        DeviceSourceModule.class,
        KnownDeviceRepositoryModule.class
})
public interface AppComponent {
    DeviceSource deviceSource();

    KnownDeviceRepository knownDeviceRepository();
}
