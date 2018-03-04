package eu.darken.bluemusic.main.ui;

import android.support.annotation.Nullable;

import javax.inject.Inject;

import eu.darken.bluemusic.settings.core.Settings;
import eu.darken.mvpbakery.base.Presenter;
import eu.darken.mvpbakery.injection.ComponentPresenter;

public class MainActivityPresenter extends ComponentPresenter<MainActivityPresenter.View, MainActivityComponent> {
    private final Settings settings;

    @Inject
    public MainActivityPresenter(Settings settings) {
        this.settings = settings;
    }

    @Override
    public void onBindChange(@Nullable View view) {
        super.onBindChange(view);
        if (getView() != null) {
            onView(v -> {
                if (settings.isShowOnboarding()) {
                    v.showOnboarding();
                } else {
                    v.showDevices();
                }
            });
        }
    }

    interface View extends Presenter.View {

        void showOnboarding();

        void showDevices();
    }
}
