package eu.darken.bluemusic.core.service;

import dagger.Subcomponent;
import eu.darken.bluemusic.util.dagger.ServiceScope;


@ServiceScope
@Subcomponent(modules = {ServiceModule.class})
public interface BlueMusicServiceComponent {
    void inject(BlueMusicService blueMusicService);
}
