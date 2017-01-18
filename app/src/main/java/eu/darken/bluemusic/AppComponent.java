package eu.darken.bluemusic;

import dagger.Component;
import dagger.MembersInjector;
import eu.darken.bluemusic.core.BlueMusicServiceComponent;
import eu.darken.bluemusic.core.bluetooth.DeviceSourceModule;
import eu.darken.bluemusic.core.database.DeviceManagerModule;
import eu.darken.bluemusic.util.AndroidModule;
import eu.darken.bluemusic.util.dagger.ApplicationScope;


@ApplicationScope
@Component(modules = {
        ActivityBinderModule.class,
        AndroidModule.class,
        DeviceSourceModule.class,
        DeviceManagerModule.class
})
public interface AppComponent extends MembersInjector<App.Injector> {

    BlueMusicServiceComponent blueMusicServiceComponent();

}
