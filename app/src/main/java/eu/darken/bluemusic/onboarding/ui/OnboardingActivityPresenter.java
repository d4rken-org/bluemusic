package eu.darken.bluemusic.onboarding.ui;

import android.support.annotation.Nullable;

import javax.inject.Inject;

import eu.darken.ommvplib.base.Presenter;
import eu.darken.ommvplib.injection.ComponentPresenter;

public class OnboardingActivityPresenter extends ComponentPresenter<OnboardingActivityPresenter.View, OnboardingActivityComponent> {

    @Inject
    public OnboardingActivityPresenter() {
    }

    @Override
    public void onBindChange(@Nullable View view) {
        super.onBindChange(view);
    }

    public interface View extends Presenter.View {
    }
}
