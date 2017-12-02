package eu.darken.bluemusic.settings.ui;

import android.support.annotation.Nullable;

import javax.inject.Inject;

import eu.darken.ommvplib.base.Presenter;
import eu.darken.ommvplib.injection.ComponentPresenter;

public class SettingsActivityPresenter extends ComponentPresenter<SettingsActivityPresenter.View, SettingsActivityComponent> {


    @Inject
    public SettingsActivityPresenter() {
    }

    @Override
    public void onBindChange(@Nullable View view) {
        super.onBindChange(view);
    }

    public interface View extends Presenter.View {
    }
}
