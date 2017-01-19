package eu.darken.bluemusic.screens;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import javax.inject.Inject;

import eu.darken.ommvplib.injection.ComponentPresenter;

public class MainActivityPresenter extends ComponentPresenter<MainActivityView, MainActivityComponent> {
    private Context context;
    @Inject
    public MainActivityPresenter(Context context) {
        this.context = context;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {

    }

    @Override
    public void onBindChange(@Nullable MainActivityView view) {
        super.onBindChange(view);

    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {

    }

    @Override
    public void onDestroy() {

    }
}
