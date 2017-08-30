package eu.darken.bluemusic.core.service;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dagger.Subcomponent;
import eu.darken.bluemusic.util.dagger.ServiceScope;
import eu.darken.ommvplib.injection.service.ServiceComponent;


@ServiceScope
@Subcomponent(modules = {ServiceModule.class})
public interface BlueMusicServiceComponent extends ServiceComponent<BlueMusicService> {

    @Subcomponent.Builder
    abstract class Builder extends ServiceComponent.Builder<BlueMusicService, BlueMusicServiceComponent> {

    }

    @javax.inject.Scope
    @Retention(RetentionPolicy.RUNTIME)
    @interface Scope {
    }
}
