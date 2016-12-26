package eu.darken.bluetoothmanager.screens.volumemanager;

import dagger.Module;
import dagger.Provides;
import eu.darken.bluetoothmanager.backend.known.KnownDeviceRepository;
import eu.darken.bluetoothmanager.util.dagger.FragmentScope;
import eu.darken.bluetoothmanager.util.mvp.PresenterFactory;


@Module
public class VolumeManagerModule {

    @Provides
    @FragmentScope
    public PresenterFactory<VolumeManagerContract.Presenter> providePresenterFactory(KnownDeviceRepository knownDeviceRepository) {
        return new PresenterFactory<VolumeManagerContract.Presenter>() {
            @Override
            public VolumeManagerContract.Presenter create() {
                return new VolumeManagerPresenter(knownDeviceRepository);
            }

            @Override
            public Class<? extends VolumeManagerContract.Presenter> getTypeClazz() {
                return VolumeManagerPresenter.class;
            }
        };
    }
}
