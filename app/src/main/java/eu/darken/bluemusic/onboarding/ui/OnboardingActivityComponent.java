package eu.darken.bluemusic.onboarding.ui;


import android.support.v4.app.Fragment;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dagger.Binds;
import dagger.Module;
import dagger.Subcomponent;
import dagger.android.support.FragmentKey;
import dagger.multibindings.IntoMap;
import eu.darken.bluemusic.onboarding.ui.intro.IntroComponent;
import eu.darken.bluemusic.onboarding.ui.intro.IntroFragment;
import eu.darken.ommvplib.injection.PresenterComponent;
import eu.darken.ommvplib.injection.activity.ActivityComponent;

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
