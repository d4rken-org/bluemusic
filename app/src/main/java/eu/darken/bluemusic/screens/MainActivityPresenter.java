package eu.darken.bluemusic.screens;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import javax.inject.Inject;

import eu.darken.bluemusic.core.Settings;
import eu.darken.bluemusic.core.service.BlueMusicService;
import eu.darken.ommvplib.injection.ComponentPresenter;
import timber.log.Timber;

public class MainActivityPresenter extends ComponentPresenter<MainActivityView, MainActivityComponent> {
    private final Context context;
    private final Settings settings;

    @Inject
    public MainActivityPresenter(Context context, Settings settings) {
        this.context = context;
        this.settings = settings;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {

    }

    @Override
    public void onBindChange(@Nullable MainActivityView view) {
        super.onBindChange(view);
        if (getView() != null) {
            Intent service = new Intent(context, BlueMusicService.class);
            if (settings.isEnabled()) {
                final ComponentName componentName = context.startService(service);
                if (componentName != null) Timber.d("Service is already running.");
            } else {
                final boolean stopService = context.stopService(service);
                Timber.d("Stopped service: %b.", stopService);
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {

    }

    @Override
    public void onDestroy() {

    }
}
