package eu.darken.bluemusic;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.preference.PreferenceManager;

import dagger.Module;
import dagger.Provides;
import eu.darken.bluemusic.util.dagger.ApplicationScope;


@Module
public class AndroidModule {
    private final App app;

    public AndroidModule(App app) {this.app = app;}

    @Provides
    @ApplicationScope
    Context context() {
        return app.getApplicationContext();
    }

    @Provides
    @ApplicationScope
    SharedPreferences preferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Provides
    @ApplicationScope
    AudioManager audioManager(Context context) {
        return (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }


}
