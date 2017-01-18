package eu.darken.bluemusic;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;
import eu.darken.bluemusic.screens.MainActivity;
import eu.darken.bluemusic.screens.MainActivityComponent;
import eu.darken.ommvplib.injection.activity.ActivityComponentBuilder;
import eu.darken.ommvplib.injection.activity.ActivityKey;

@Module(subcomponents = {MainActivityComponent.class})
abstract class ActivityBinderModule {

    @Binds
    @IntoMap
    @ActivityKey(MainActivity.class)
    abstract ActivityComponentBuilder mainActivity(MainActivityComponent.Builder impl);
}