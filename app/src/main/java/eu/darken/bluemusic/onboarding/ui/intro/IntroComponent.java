package eu.darken.bluemusic.onboarding.ui.intro;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dagger.Subcomponent;
import eu.darken.mvpbakery.injection.PresenterComponent;
import eu.darken.mvpbakery.injection.fragment.FragmentComponent;


@IntroComponent.Scope
@Subcomponent()
public interface IntroComponent extends PresenterComponent<IntroPresenter, IntroComponent>, FragmentComponent<IntroFragment> {
    @Subcomponent.Builder
    abstract class Builder extends FragmentComponent.Builder<IntroFragment, IntroComponent> {

    }

    @Documented
    @javax.inject.Scope
    @Retention(RetentionPolicy.RUNTIME)
    @interface Scope {
    }
}
