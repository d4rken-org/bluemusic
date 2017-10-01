package eu.darken.bluemusic.core.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.StringRes;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import javax.inject.Inject;

import eu.darken.bluemusic.R;
import eu.darken.bluemusic.util.dagger.ServiceScope;

@ServiceScope
public class ServiceHelper {

    private final static String NOTIFICATION_CHANNEL_ID = "notification.channel.core";
    private final static int NOTIFICATION_ID = 1;
    private final NotificationManager notificationManager;
    private final NotificationCompat.Builder builder;
    private final Service service;

    @Inject
    public ServiceHelper(BlueMusicService service, NotificationManager notificationManager) {
        this.service = service;
        this.notificationManager = notificationManager;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, getString(R.string.notification_channel_label_status), NotificationManagerCompat.IMPORTANCE_MIN);
            notificationManager.createNotificationChannel(channel);
        }
        builder = new NotificationCompat.Builder(service, NOTIFICATION_CHANNEL_ID)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.app_name));
    }

    private String getString(@StringRes int stringRes) {
        return service.getString(stringRes);
    }

    public void startForeground() {
        service.startForeground(NOTIFICATION_ID, builder.build());
    }

    public void stopForeground() {
        service.stopForeground(true);
    }

    public static ComponentName startService(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.startForegroundService(intent);
        } else {
            return context.startService(intent);
        }
    }
}
