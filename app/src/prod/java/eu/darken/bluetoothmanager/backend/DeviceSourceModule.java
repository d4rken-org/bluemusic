package eu.darken.bluetoothmanager.backend;

import android.content.Context;

import dagger.Module;
import dagger.Provides;
import eu.darken.bluetoothmanager.backend.live.DeviceSource;
import eu.darken.bluetoothmanager.backend.live.LiveDeviceSource;
import eu.darken.bluetoothmanager.util.dagger.ApplicationScope;


@Module
public class DeviceSourceModule {
    private final Context context;

    public DeviceSourceModule(Context context) {this.context = context;}

    @Provides
    @ApplicationScope
    DeviceSource provideDeviceSource() {
        return new LiveDeviceSource(context);
    }
}
