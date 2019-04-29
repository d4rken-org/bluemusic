package eu.darken.bluemusic.main.ui.config;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dagger.Subcomponent;
import eu.darken.mvpbakery.injection.PresenterComponent;
import eu.darken.mvpbakery.injection.fragment.FragmentComponent;


@ConfigComponent.Scope
@Subcomponent()
public interface ConfigComponent extends PresenterComponent<ConfigPresenter, ConfigComponent>, FragmentComponent<ConfigFragment> {
    @Subcomponent.Builder
    abstract class Builder extends FragmentComponent.Builder<ConfigFragment, ConfigComponent> {

    }

    @Documented
    @javax.inject.Scope
    @Retention(RetentionPolicy.RUNTIME)
    @interface Scope {
    }
}
