package eu.darken.bluemusic;

import android.app.Service;
import dagger.Binds;
import dagger.Module;
import dagger.android.AndroidInjector;
import dagger.multibindings.IntoMap;
import eu.darken.bluemusic.main.core.service.BlueMusicService;
import eu.darken.bluemusic.main.core.service.BlueMusicServiceComponent;
import eu.darken.mvpbakery.injection.service.ServiceKey;

@Module(subcomponents = {BlueMusicServiceComponent.class})
abstract class ServiceBinderModule {

    @Binds
    @IntoMap
    @ServiceKey(BlueMusicService.class)
    abstract AndroidInjector.Factory<? extends Service> blueService(BlueMusicServiceComponent.Builder impl);
}