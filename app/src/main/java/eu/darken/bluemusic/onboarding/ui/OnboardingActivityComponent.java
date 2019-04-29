package eu.darken.bluemusic.onboarding.ui;


import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.fragment.app.Fragment;
import dagger.Binds;
import dagger.Module;
import dagger.Subcomponent;
import dagger.multibindings.IntoMap;
import eu.darken.bluemusic.onboarding.ui.intro.IntroComponent;
import eu.darken.bluemusic.onboarding.ui.intro.IntroFragment;
import eu.darken.mvpbakery.injection.PresenterComponent;
import eu.darken.mvpbakery.injection.activity.ActivityComponent;
import eu.darken.mvpbakery.injection.fragment.FragmentKey;

@OnboardingActivityComponent.Scope
@Subcomponent(modules = {
        OnboardingActivityComponent.FragmentBinderModule.class
})
public interface OnboardingActivityComponent extends ActivityComponent<OnboardingActivity>, PresenterComponent<OnboardingActivityPresenter.View, OnboardingActivityPresenter> {

    @Subcomponent.Builder
    abstract class Builder extends ActivityComponent.Builder<OnboardingActivity, OnboardingActivityComponent> {

    }

    @javax.inject.Scope
    @Retention(RetentionPolicy.RUNTIME)
    @interface Scope {
    }

    @Module(subcomponents = {
            IntroComponent.class
    })
    abstract class FragmentBinderModule {

        @Binds
        @IntoMap
        @FragmentKey(IntroFragment.class)
        abstract Factory<? extends Fragment> discover(IntroComponent.Builder impl);

    }
}
