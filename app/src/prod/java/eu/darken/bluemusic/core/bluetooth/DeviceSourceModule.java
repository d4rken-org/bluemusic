package eu.darken.bluemusic.core.bluetooth;

import android.content.Context;

import dagger.Module;
import dagger.Provides;
import eu.darken.bluemusic.util.dagger.ApplicationScope;


@Module
public class DeviceSourceModule {
    @Provides
    @ApplicationScope
    BluetoothSource provideDeviceSource(Context context) {
        return new LiveBluetoothSource(context);
    }
}
