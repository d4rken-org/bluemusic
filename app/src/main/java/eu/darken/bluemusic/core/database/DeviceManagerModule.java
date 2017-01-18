package eu.darken.bluemusic.core.database;


import dagger.Module;
import dagger.Provides;
import eu.darken.bluemusic.core.bluetooth.BluetoothSource;
import eu.darken.bluemusic.util.dagger.ApplicationScope;

@Module
public class DeviceManagerModule {

    @Provides
    @ApplicationScope
    public DeviceManager provideManagedDeviceRepo(BluetoothSource bluetoothSource) {
        return new DeviceManager(bluetoothSource);
    }
}
