package eu.darken.bluemusic.screens.devices;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dagger.Subcomponent;
import eu.darken.ommvplib.injection.PresenterComponent;
import eu.darken.ommvplib.injection.fragment.support.SupportFragmentComponent;


@DevicesComponent.Scope
@Subcomponent(modules = {})
public interface DevicesComponent extends PresenterComponent<DevicesPresenter.View, DevicesPresenter>, SupportFragmentComponent<DevicesFragment> {
    @Subcomponent.Builder
    abstract class Builder extends SupportFragmentComponent.Builder<DevicesFragment, DevicesComponent> {

    }

    @Documented
    @javax.inject.Scope
    @Retention(RetentionPolicy.RUNTIME)
    @interface Scope {
    }
}
