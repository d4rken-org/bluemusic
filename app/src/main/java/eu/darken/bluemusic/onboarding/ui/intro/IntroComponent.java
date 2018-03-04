package eu.darken.bluemusic.onboarding.ui.intro;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dagger.Subcomponent;
import eu.darken.mvpbakery.injection.PresenterComponent;
import eu.darken.mvpbakery.injection.fragment.support.SupportFragmentComponent;


@IntroComponent.Scope
@Subcomponent()
public interface IntroComponent extends PresenterComponent<IntroPresenter.View, IntroPresenter>, SupportFragmentComponent<IntroFragment> {
    @Subcomponent.Builder
    abstract class Builder extends SupportFragmentComponent.Builder<IntroFragment, IntroComponent> {

    }

    @Documented
    @javax.inject.Scope
    @Retention(RetentionPolicy.RUNTIME)
    @interface Scope {
    }
}
