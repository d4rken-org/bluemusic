package eu.darken.bluetoothmanager.screens.volumemanager;

import dagger.Module;
import dagger.Provides;
import eu.darken.bluetoothmanager.core.device.ManagedDeviceRepo;
import eu.darken.bluetoothmanager.util.dagger.FragmentScope;
import eu.darken.bluetoothmanager.util.mvp.PresenterFactory;


@Module
public class VolumeManagerModule {

    @Provides
    @FragmentScope
    public PresenterFactory<VolumeManagerContract.Presenter> providePresenterFactory(ManagedDeviceRepo managedDeviceRepo) {
        return new PresenterFactory<VolumeManagerContract.Presenter>() {
            @Override
            public VolumeManagerContract.Presenter create() {
                return new VolumeManagerPresenter(managedDeviceRepo);
            }

            @Override
            public Class<? extends VolumeManagerContract.Presenter> getTypeClazz() {
                return VolumeManagerPresenter.class;
            }
        };
    }
}
