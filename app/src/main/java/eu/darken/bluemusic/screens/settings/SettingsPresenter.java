package eu.darken.bluemusic.screens.settings;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import javax.inject.Inject;

import eu.darken.ommvplib.base.Presenter;
import eu.darken.ommvplib.injection.ComponentPresenter;

@SettingsScope
public class SettingsPresenter extends ComponentPresenter<SettingsPresenter.View, SettingsComponent> {


    @Inject
    SettingsPresenter() {
    }

    @Override
    public void onCreate(Bundle bundle) {

    }

    @Override
    public void onBindChange(@Nullable View view) {
        super.onBindChange(view);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle bundle) {

    }

    @Override
    public void onDestroy() {

    }

    public interface View extends Presenter.View {
    }
}
