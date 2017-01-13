package eu.darken.bluemusic.core.database;


import dagger.Module;
import dagger.Provides;
import eu.darken.bluemusic.core.bluetooth.BluetoothSource;
import eu.darken.bluemusic.util.dagger.ApplicationScope;

@Module
@ApplicationScope
public class ManagedDeviceRepoModule {

    @Provides
    ManagedDeviceRepo providesKnownDeviceRepository(BluetoothSource bluetoothSource) {
        return new ManagedDeviceRepo(bluetoothSource);
    }
}
