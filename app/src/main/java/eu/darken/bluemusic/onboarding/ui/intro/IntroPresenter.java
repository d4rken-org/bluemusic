package eu.darken.bluemusic.onboarding.ui.intro;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import eu.darken.bluemusic.settings.core.Settings;
import eu.darken.bluemusic.util.ApiHelper;
import eu.darken.mvpbakery.base.Presenter;
import eu.darken.mvpbakery.injection.ComponentPresenter;
import timber.log.Timber;

@IntroComponent.Scope
public class IntroPresenter extends ComponentPresenter<IntroPresenter.View, IntroComponent> {

    private final Settings settings;

    @Inject
    IntroPresenter(Settings settings) {
        this.settings = settings;
    }

    @Override
    public void onBindChange(@Nullable View view) {
        super.onBindChange(view);
    }

    public void finishOnboarding(Context context) {
        if (ApiHelper.hasAndroid12() && ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Timber.w("BLUETOOTH_CONNECT permission is missing");
            onView(View::requestBluetoothConnectPermission);
            return;
        }

        Timber.i("Setup is complete");
        settings.setShowOnboarding(false);
        onView(View::closeScreen);
    }


    public interface View extends Presenter.View {
        void requestBluetoothConnectPermission();

        void closeScreen();
    }
}
