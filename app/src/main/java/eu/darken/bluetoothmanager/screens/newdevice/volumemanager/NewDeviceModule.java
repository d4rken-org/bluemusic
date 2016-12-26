package eu.darken.bluetoothmanager.screens.newdevice.volumemanager;

import dagger.Module;
import dagger.Provides;
import eu.darken.bluetoothmanager.backend.live.DeviceSource;
import eu.darken.bluetoothmanager.backend.known.KnownDeviceRepository;
import eu.darken.bluetoothmanager.util.dagger.ActivityScope;
import eu.darken.bluetoothmanager.util.mvp.PresenterFactory;


@Module
public class NewDeviceModule {

    @Provides
    @ActivityScope
    public PresenterFactory<NewDeviceContract.Presenter> providePresenterFactory(DeviceSource deviceSource, KnownDeviceRepository knownDeviceRepository) {
        return new PresenterFactory<NewDeviceContract.Presenter>() {
            @Override
            public NewDeviceContract.Presenter create() {
                return new NewDevicePresenter(deviceSource, knownDeviceRepository);
            }

            @Override
            public Class<? extends NewDeviceContract.Presenter> getTypeClazz() {
                return NewDevicePresenter.class;
            }
        };
    }
}
