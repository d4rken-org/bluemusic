package eu.darken.bluemusic.main.ui;

import android.support.annotation.Nullable;

import javax.inject.Inject;

import eu.darken.ommvplib.injection.ComponentPresenter;

public class MainActivityPresenter extends ComponentPresenter<MainActivityView, MainActivityComponent> {


    @Inject
    public MainActivityPresenter() {
    }

    @Override
    public void onBindChange(@Nullable MainActivityView view) {
        super.onBindChange(view);
    }
}
