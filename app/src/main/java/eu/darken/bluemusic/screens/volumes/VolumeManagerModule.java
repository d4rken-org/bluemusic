package eu.darken.bluemusic.screens.volumes;

import dagger.Module;
import dagger.Provides;
import eu.darken.bluemusic.core.database.ManagedDeviceRepo;
import eu.darken.bluemusic.util.dagger.FragmentScope;
import eu.darken.bluemusic.util.mvp.PresenterFactory;


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
