package eu.darken.bluemusic.screens.settings;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import javax.inject.Inject;

import eu.darken.bluemusic.core.service.BlueMusicService;
import eu.darken.ommvplib.base.Presenter;
import eu.darken.ommvplib.injection.ComponentPresenter;
import timber.log.Timber;

@SettingsScope
public class SettingsPresenter extends ComponentPresenter<SettingsPresenter.View, SettingsComponent> {

    private final Context context;

    @Inject
    SettingsPresenter(Context context) {
        this.context = context;
    }

    @Override
    public void onCreate(Bundle bundle) {
    }

    @Override
    public void onBindChange(@Nullable View view) {
        super.onBindChange(view);
    }

    public void onSaveInstanceState(@NonNull Bundle bundle) {

    }

    void toggleService(boolean run) {
        Intent service = new Intent(context, BlueMusicService.class);
        if (run) {
            final ComponentName componentName = context.startService(service);
            if (componentName != null) Timber.d("Service is already running.");
        } else {
            final boolean stopService = context.stopService(service);
            Timber.d("Stopped service: %b.", stopService);
        }
    }

    @Override
    public void onDestroy() {

    }

    public interface View extends Presenter.View {
    }
}
