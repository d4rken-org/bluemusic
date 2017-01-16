package eu.darken.bluemusic.core.database;


import dagger.Module;
import dagger.Provides;
import eu.darken.bluemusic.core.bluetooth.BluetoothSource;
import eu.darken.bluemusic.util.dagger.ApplicationScope;

@Module
public class ManagedDeviceRepoModule {

    @Provides
    @ApplicationScope
    public ManagedDeviceRepo provideManagedDeviceRepo(BluetoothSource bluetoothSource) {
        return new ManagedDeviceRepo(bluetoothSource);
    }
}
