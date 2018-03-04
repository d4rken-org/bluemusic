package eu.darken.bluemusic.onboarding.ui;

import android.support.annotation.Nullable;

import javax.inject.Inject;

import eu.darken.mvpbakery.base.Presenter;
import eu.darken.mvpbakery.injection.ComponentPresenter;

public class OnboardingActivityPresenter extends ComponentPresenter<OnboardingActivityPresenter.View, OnboardingActivityComponent> {

    @Inject
    public OnboardingActivityPresenter() {
    }

    @Override
    public void onBindChange(@Nullable View view) {
        super.onBindChange(view);
        onView(View::showIntro);
    }

    public interface View extends Presenter.View {
        void showIntro();
    }
}
