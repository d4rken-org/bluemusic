package eu.darken.bluemusic

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.PowerManager
import android.preference.PreferenceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
@Module
class AndroidModule {

    @Provides fun preferences(@ApplicationContext context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    @Provides fun audioManager(@ApplicationContext context: Context): AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Provides fun powerManager(@ApplicationContext context: Context): PowerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    @Provides fun notificationManager(@ApplicationContext context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Provides fun packageManager(@ApplicationContext context: Context): PackageManager = context.packageManager
}
