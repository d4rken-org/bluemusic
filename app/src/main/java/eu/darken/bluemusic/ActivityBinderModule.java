package eu.darken.bluemusic;

import android.app.Activity;

import dagger.Binds;
import dagger.Module;
import dagger.android.ActivityKey;
import dagger.android.AndroidInjector;
import dagger.multibindings.IntoMap;
import eu.darken.bluemusic.bluetooth.ui.BluetoothActivity;
import eu.darken.bluemusic.bluetooth.ui.BluetoothActivityComponent;
import eu.darken.bluemusic.main.ui.MainActivity;
import eu.darken.bluemusic.main.ui.MainActivityComponent;
import eu.darken.bluemusic.onboarding.ui.OnboardingActivity;
import eu.darken.bluemusic.onboarding.ui.OnboardingActivityComponent;
import eu.darken.bluemusic.settings.ui.SettingsActivity;
import eu.darken.bluemusic.settings.ui.SettingsActivityComponent;

@Module(subcomponents = {
        MainActivityComponent.class,
        SettingsActivityComponent.class,
        BluetoothActivityComponent.class,
        OnboardingActivityComponent.class
})
abstract class ActivityBinderModule {

    @Binds
    @IntoMap
    @ActivityKey(MainActivity.class)
    abstract AndroidInjector.Factory<? extends Activity> main(MainActivityComponent.Builder impl);

    @Binds
    @IntoMap
    @ActivityKey(SettingsActivity.class)
    abstract AndroidInjector.Factory<? extends Activity> settings(SettingsActivityComponent.Builder impl);

    @Binds
    @IntoMap
    @ActivityKey(BluetoothActivity.class)
    abstract AndroidInjector.Factory<? extends Activity> bluetooth(BluetoothActivityComponent.Builder impl);

    @Binds
    @IntoMap
    @ActivityKey(OnboardingActivity.class)
    abstract AndroidInjector.Factory<? extends Activity> onboarding(OnboardingActivityComponent.Builder impl);
}