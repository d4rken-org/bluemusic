package eu.darken.bluemusic.core.bluetooth;

import android.content.Context;

import dagger.Module;
import dagger.Provides;
import eu.darken.bluemusic.AppComponent;


@Module
public class DeviceSourceModule {
    @Provides
    @AppComponent.Scope
    BluetoothSource provideDeviceSource(Context context) {
        return new LiveBluetoothSource(context);
    }
}
