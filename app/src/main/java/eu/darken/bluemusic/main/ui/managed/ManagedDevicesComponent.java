package eu.darken.bluemusic.main.ui.managed;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dagger.Subcomponent;
import eu.darken.ommvplib.injection.PresenterComponent;
import eu.darken.ommvplib.injection.fragment.support.SupportFragmentComponent;


@ManagedDevicesComponent.Scope
@Subcomponent(modules = {})
public interface ManagedDevicesComponent extends PresenterComponent<ManagedDevicesPresenter.View, ManagedDevicesPresenter>, SupportFragmentComponent<ManagedDevicesFragment> {
    @Subcomponent.Builder
    abstract class Builder extends SupportFragmentComponent.Builder<ManagedDevicesFragment, ManagedDevicesComponent> {

    }

    @Documented
    @javax.inject.Scope
    @Retention(RetentionPolicy.RUNTIME)
    @interface Scope {
    }
}
