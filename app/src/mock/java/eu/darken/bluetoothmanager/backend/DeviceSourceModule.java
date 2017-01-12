package eu.darken.bluetoothmanager.core;

import android.content.Context;

import dagger.Module;
import dagger.Provides;
import eu.darken.bluetoothmanager.util.dagger.ApplicationScope;


@Module
public class DeviceSourceModule {
    private final Context context;

    public DeviceSourceModule(Context context) {this.context = context;}

    @Provides
    @ApplicationScope
    DeviceSource provideDeviceSource() {
        return new FakeDeviceSource(context);
    }
}
