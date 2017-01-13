package eu.darken.bluemusic.core;

import dagger.Component;
import eu.darken.bluemusic.AppComponent;
import eu.darken.bluemusic.util.dagger.ServiceScope;


@ServiceScope
@Component(dependencies = AppComponent.class)
public interface BlueMusicServiceComponent {
    void inject(BlueMusicService blueMusicService);
}
