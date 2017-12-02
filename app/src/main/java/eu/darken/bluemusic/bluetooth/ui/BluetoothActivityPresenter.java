package eu.darken.bluemusic.bluetooth.ui;

import android.support.annotation.Nullable;

import javax.inject.Inject;

import eu.darken.ommvplib.base.Presenter;
import eu.darken.ommvplib.injection.ComponentPresenter;

public class BluetoothActivityPresenter extends ComponentPresenter<BluetoothActivityPresenter.View, BluetoothActivityComponent> {


    @Inject
    public BluetoothActivityPresenter() {
    }

    @Override
    public void onBindChange(@Nullable View view) {
        super.onBindChange(view);
    }

    public interface View extends Presenter.View {
    }
}
