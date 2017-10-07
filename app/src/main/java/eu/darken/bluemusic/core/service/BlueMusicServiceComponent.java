package eu.darken.bluemusic.core.service;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dagger.Subcomponent;
import eu.darken.ommvplib.injection.service.ServiceComponent;


@BlueMusicServiceComponent.Scope
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
