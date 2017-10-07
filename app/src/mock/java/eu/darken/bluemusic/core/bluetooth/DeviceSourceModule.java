package eu.darken.bluemusic.core.bluetooth;

import dagger.Module;
import dagger.Provides;
import eu.darken.bluemusic.AppComponent;


@Module
public class DeviceSourceModule {
    @Provides
    @AppComponent.Scope
    BluetoothSource provideDeviceSource() {
        return new FakeDeviceSource();
    }
}
