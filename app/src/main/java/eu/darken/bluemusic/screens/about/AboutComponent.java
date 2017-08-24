package eu.darken.bluemusic.screens.about;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dagger.Subcomponent;
import eu.darken.ommvplib.injection.PresenterComponent;
import eu.darken.ommvplib.injection.fragment.support.SupportFragmentComponent;


@AboutComponent.Scope
@Subcomponent(modules = {})
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