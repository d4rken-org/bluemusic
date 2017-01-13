package eu.darken.bluemusic.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import dagger.Module;
import dagger.Provides;
import eu.darken.bluemusic.App;
import eu.darken.bluemusic.util.dagger.ApplicationScope;


@Module
public class AndroidModule {
    private final App app;

    public AndroidModule(App app) {this.app = app;}

    @Provides
    @ApplicationScope
    Context provideContext() {
        return app.getApplicationContext();
    }

    @Provides
    @ApplicationScope
    SharedPreferences providePreferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

}
