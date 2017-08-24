package eu.darken.bluemusic;

import android.app.Activity;

import dagger.Binds;
import dagger.Module;
import dagger.android.ActivityKey;
import dagger.android.AndroidInjector;
import dagger.multibindings.IntoMap;
import eu.darken.bluemusic.screens.MainActivity;
import eu.darken.bluemusic.screens.MainActivityComponent;

@Module(subcomponents = {MainActivityComponent.class})
abstract class ActivityBinderModule {

    @Binds
    @IntoMap
    @ActivityKey(MainActivity.class)
    abstract AndroidInjector.Factory<? extends Activity> mainActivity(MainActivityComponent.Builder impl);
}