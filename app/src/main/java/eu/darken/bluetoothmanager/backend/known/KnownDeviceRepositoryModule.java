package eu.darken.bluetoothmanager.backend.known;


import dagger.Module;
import dagger.Provides;
import eu.darken.bluetoothmanager.util.dagger.ApplicationScope;

@Module
@ApplicationScope
public class KnownDeviceRepositoryModule {

    @Provides
    KnownDeviceRepository providesKnownDeviceRepository() {
        return new KnownDeviceRepository();
    }
}
