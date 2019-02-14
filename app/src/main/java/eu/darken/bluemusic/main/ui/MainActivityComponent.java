package eu.darken.bluemusic.main.ui;


import android.support.v4.app.Fragment;
import dagger.Binds;
import dagger.Module;
import dagger.Subcomponent;
import dagger.multibindings.IntoMap;
import eu.darken.bluemusic.bluetooth.ui.discover.DiscoverComponent;
import eu.darken.bluemusic.bluetooth.ui.discover.DiscoverFragment;
import eu.darken.bluemusic.main.ui.config.ConfigComponent;
import eu.darken.bluemusic.main.ui.config.ConfigFragment;
import eu.darken.bluemusic.main.ui.managed.ManagedDevicesComponent;
import eu.darken.bluemusic.main.ui.managed.ManagedDevicesFragment;
import eu.darken.mvpbakery.injection.PresenterComponent;
import eu.darken.mvpbakery.injection.activity.ActivityComponent;
import eu.darken.mvpbakery.injection.fragment.FragmentKey;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@MainActivityComponent.Scope
@Subcomponent(modules = {
        MainActivityComponent.FragmentBinderModule.class
})
public interface MainActivityComponent extends ActivityComponent<MainActivity>, PresenterComponent<MainActivityPresenter.View, MainActivityPresenter> {

    @Subcomponent.Builder
    abstract class Builder extends ActivityComponent.Builder<MainActivity, MainActivityComponent> {

    }

    @javax.inject.Scope
    @Retention(RetentionPolicy.RUNTIME)
    @interface Scope {
    }

    @Module(subcomponents = {
            ManagedDevicesComponent.class,
            DiscoverComponent.class,
            ConfigComponent.class
    })
    abstract class FragmentBinderModule {

        @Binds
        @IntoMap
        @FragmentKey(ManagedDevicesFragment.class)
        abstract Factory<? extends Fragment> volumes(ManagedDevicesComponent.Builder impl);

        @Binds
        @IntoMap
        @FragmentKey(ConfigFragment.class)
        abstract Factory<? extends Fragment> config(ConfigComponent.Builder impl);

        @Binds
        @IntoMap
        @FragmentKey(DiscoverFragment.class)
        abstract Factory<? extends Fragment> devices(DiscoverComponent.Builder impl);
    }
}
