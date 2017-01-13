package eu.darken.bluemusic.core;

import android.content.Context;

import dagger.Module;
import dagger.Provides;
import eu.darken.bluemusic.core.bluetooth.BluetoothSource;
import eu.darken.bluemusic.core.bluetooth.LiveBluetoothSource;
import eu.darken.bluemusic.util.dagger.ApplicationScope;


@Module
public class DeviceSourceModule {
    private final Context context;

    public DeviceSourceModule(Context context) {this.context = context;}

    @Provides
    @ApplicationScope
    BluetoothSource provideDeviceSource() {
        return new LiveBluetoothSource(context);
    }
}
