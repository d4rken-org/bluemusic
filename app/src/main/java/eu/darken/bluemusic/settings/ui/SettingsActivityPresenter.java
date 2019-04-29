package eu.darken.bluemusic.settings.ui;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import eu.darken.mvpbakery.base.Presenter;
import eu.darken.mvpbakery.injection.ComponentPresenter;


public class SettingsActivityPresenter extends ComponentPresenter<SettingsActivityPresenter.View, SettingsActivityComponent> {

    @Inject
    public SettingsActivityPresenter() {
    }

    @Override
    public void onBindChange(@Nullable View view) {
        super.onBindChange(view);
        onView(View::showSettings);
    }

    public interface View extends Presenter.View {

        void showSettings();
    }
}
