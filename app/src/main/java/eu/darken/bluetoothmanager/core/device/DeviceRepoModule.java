package eu.darken.bluetoothmanager.core.device;


import dagger.Module;
import dagger.Provides;
import eu.darken.bluetoothmanager.core.manager.BluetoothSource;
import eu.darken.bluetoothmanager.util.dagger.ApplicationScope;

@Module
@ApplicationScope
public class DeviceRepoModule {

    @Provides
    ManagedDeviceRepo providesKnownDeviceRepository(BluetoothSource bluetoothSource) {
        return new ManagedDeviceRepo(bluetoothSource);
    }
}
