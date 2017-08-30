package eu.darken.bluemusic;

import dagger.Component;
import dagger.MembersInjector;
import eu.darken.bluemusic.core.bluetooth.DeviceSourceModule;
import eu.darken.bluemusic.util.dagger.ApplicationScope;


@ApplicationScope
@Component(modules = {
        ActivityBinderModule.class,
        ServiceBinderModule.class,
        ReceiverBinderModule.class,
        AndroidModule.class,
        DeviceSourceModule.class
})
interface AppComponent extends MembersInjector<App> {
    void inject(App app);

    @Component.Builder
    interface Builder {
        Builder androidModule(AndroidModule module);

        AppComponent build();
    }

}
