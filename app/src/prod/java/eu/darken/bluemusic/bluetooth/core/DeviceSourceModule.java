package eu.darken.bluemusic.bluetooth.core;

import android.content.Context;

import dagger.Module;
import dagger.Provides;
import eu.darken.bluemusic.AppComponent;
import eu.darken.bluemusic.main.core.database.RealmSource;
import eu.darken.bluemusic.settings.core.Settings;


@Module
public class DeviceSourceModule {
    @Provides
    @AppComponent.Scope
    BluetoothSource provideDeviceSource(Context context, Settings settings, RealmSource realmSource, FakeSpeakerDevice fakeSpeakerDevice) {
        return new LiveBluetoothSource(context, settings, realmSource, fakeSpeakerDevice);
    }
}
