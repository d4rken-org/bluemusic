package eu.darken.bluemusic.bluetooth.ui.discover;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dagger.Subcomponent;
import eu.darken.mvpbakery.injection.PresenterComponent;
import eu.darken.mvpbakery.injection.fragment.FragmentComponent;


@DiscoverComponent.Scope
@Subcomponent()
public interface DiscoverComponent extends PresenterComponent<DiscoverPresenter, DiscoverComponent>, FragmentComponent<DiscoverFragment> {
    @Subcomponent.Builder
    abstract class Builder extends FragmentComponent.Builder<DiscoverFragment, DiscoverComponent> {

    }

    @Documented
    @javax.inject.Scope
    @Retention(RetentionPolicy.RUNTIME)
    @interface Scope {
    }
}
