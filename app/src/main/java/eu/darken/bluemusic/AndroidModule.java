package eu.darken.bluemusic;

import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import dagger.Module;
import dagger.Provides;


@Module
public class AndroidModule {

    @Provides
    @AppComponent.Scope
    Context context(Application application) {
        return application.getApplicationContext();
    }

    @Provides
    @AppComponent.Scope
    SharedPreferences preferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Provides
    @AppComponent.Scope
    AudioManager audioManager(Context context) {
        return (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    @Provides
    @AppComponent.Scope
    PowerManager powerManager(Context context) {
        return (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    }

    @Provides
    @AppComponent.Scope
    NotificationManager notificationManager(Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Provides
    @AppComponent.Scope
    PackageManager packageManager(Context context) {
        return context.getPackageManager();
    }
}
