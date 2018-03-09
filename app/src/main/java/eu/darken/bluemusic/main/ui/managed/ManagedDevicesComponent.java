package eu.darken.bluemusic.main.ui.managed;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dagger.Subcomponent;
import eu.darken.mvpbakery.injection.PresenterComponent;
import eu.darken.mvpbakery.injection.fragment.FragmentComponent;


@ManagedDevicesComponent.Scope
@Subcomponent()
public interface ManagedDevicesComponent extends PresenterComponent<ManagedDevicesPresenter.View, ManagedDevicesPresenter>, FragmentComponent<ManagedDevicesFragment> {
    @Subcomponent.Builder
    abstract class Builder extends FragmentComponent.Builder<ManagedDevicesFragment, ManagedDevicesComponent> {

    }

    @Documented
    @javax.inject.Scope
    @Retention(RetentionPolicy.RUNTIME)
    @interface Scope {
    }
}
