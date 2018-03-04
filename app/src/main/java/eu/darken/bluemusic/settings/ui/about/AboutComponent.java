package eu.darken.bluemusic.settings.ui.about;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dagger.Subcomponent;
import eu.darken.mvpbakery.injection.PresenterComponent;
import eu.darken.mvpbakery.injection.fragment.support.SupportFragmentComponent;


@AboutComponent.Scope
@Subcomponent()
public interface AboutComponent extends PresenterComponent<AboutPresenter.View, AboutPresenter>, SupportFragmentComponent<AboutFragment> {
    @Subcomponent.Builder
    abstract class Builder extends SupportFragmentComponent.Builder<AboutFragment, AboutComponent> {

    }

    @Documented
    @javax.inject.Scope
    @Retention(RetentionPolicy.RUNTIME)
    @interface Scope {
    }
}