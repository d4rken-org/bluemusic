package eu.darken.bluemusic.core;

import dagger.Subcomponent;
import eu.darken.bluemusic.util.dagger.ServiceScope;


@ServiceScope
@Subcomponent(modules = {})
public interface BlueMusicServiceComponent {
    void inject(BlueMusicService blueMusicService);
}
