package eu.darken.bluetoothmanager.screens.newdevice;

import dagger.Module;
import dagger.Provides;
import eu.darken.bluetoothmanager.core.manager.BluetoothSource;
import eu.darken.bluetoothmanager.core.device.ManagedDeviceRepo;
import eu.darken.bluetoothmanager.util.dagger.ActivityScope;
import eu.darken.bluetoothmanager.util.mvp.PresenterFactory;


@Module
public class NewDeviceModule {

    @Provides
    @ActivityScope
    public PresenterFactory<NewDeviceContract.Presenter> providePresenterFactory(BluetoothSource bluetoothSource, ManagedDeviceRepo managedDeviceRepo) {
        return new PresenterFactory<NewDeviceContract.Presenter>() {
            @Override
            public NewDeviceContract.Presenter create() {
                return new NewDevicePresenter(bluetoothSource, managedDeviceRepo);
            }

            @Override
            public Class<? extends NewDeviceContract.Presenter> getTypeClazz() {
                return NewDevicePresenter.class;
            }
        };
    }
}
