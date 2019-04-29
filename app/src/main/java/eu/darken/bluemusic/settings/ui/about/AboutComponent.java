package eu.darken.bluemusic.settings.ui.about;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dagger.Subcomponent;
import eu.darken.mvpbakery.injection.PresenterComponent;
import eu.darken.mvpbakery.injection.fragment.FragmentComponent;


@AboutComponent.Scope
@Subcomponent()
public interface AboutComponent extends PresenterComponent<AboutPresenter, AboutComponent>, FragmentComponent<AboutFragment> {
    @Subcomponent.Builder
    abstract class Builder extends FragmentComponent.Builder<AboutFragment, AboutComponent> {

    }

    @Documented
    @javax.inject.Scope
    @Retention(RetentionPolicy.RUNTIME)
    @interface Scope {
    }
}