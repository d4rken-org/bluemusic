package eu.darken.bluemusic.screens.devices;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import javax.inject.Inject;

import eu.darken.ommvplib.base.Presenter;
import eu.darken.ommvplib.injection.ComponentPresenter;

@DevicesComponent.Scope
public class DevicesPresenter extends ComponentPresenter<DevicesPresenter.View, DevicesComponent> {


    @Inject
    DevicesPresenter() {
    }

    @Override
    public void onCreate(Bundle bundle) {

    }

    @Override
    public void onBindChange(@Nullable View view) {

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
