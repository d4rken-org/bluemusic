package eu.darken.bluemusic.onboarding.ui.intro;

import android.support.annotation.Nullable;

import javax.inject.Inject;

import eu.darken.bluemusic.settings.core.Settings;
import eu.darken.mvpbakery.base.Presenter;
import eu.darken.mvpbakery.injection.ComponentPresenter;

@IntroComponent.Scope
public class IntroPresenter extends ComponentPresenter<IntroPresenter.View, IntroComponent> {

    private final Settings settings;

    @Inject
    IntroPresenter(Settings settings) {
        this.settings = settings;
    }

    @Override
    public void onBindChange(@Nullable View view) {
        super.onBindChange(view);
    }

    public void onFinishOnboardingClicked() {
        settings.setShowOnboarding(false);
        onView(View::closeScreen);
    }


    public interface View extends Presenter.View {
        void closeScreen();
    }
}
