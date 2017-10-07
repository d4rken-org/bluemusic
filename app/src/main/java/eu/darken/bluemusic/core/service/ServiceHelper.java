package eu.darken.bluemusic.core.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import java.util.Collection;
import java.util.Iterator;

import javax.inject.Inject;

import eu.darken.bluemusic.R;
import eu.darken.bluemusic.ResHelper;
import eu.darken.bluemusic.core.database.ManagedDevice;
import eu.darken.bluemusic.screens.MainActivity;
import eu.darken.bluemusic.util.dagger.ServiceScope;
import timber.log.Timber;

@ServiceScope
public class ServiceHelper {

    private final static String NOTIFICATION_CHANNEL_ID = "notification.channel.core";
    private final static int NOTIFICATION_ID = 1;
    private final NotificationManager notificationManager;
    private final ResHelper resHelper;
    private final NotificationCompat.Builder builder;
    private final Service service;
    private boolean started;

    @Inject
    public ServiceHelper(BlueMusicService service, NotificationManager notificationManager, ResHelper resHelper) {
        this.service = service;
        this.notificationManager = notificationManager;
        this.resHelper = resHelper;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, resHelper.getString(R.string.label_notification_channel_status), NotificationManagerCompat.IMPORTANCE_MIN);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(service, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(service, 0, intent, 0);

        builder = new NotificationCompat.Builder(service, NOTIFICATION_CHANNEL_ID)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setContentIntent(pi)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentText(resHelper.getString(R.string.label_status_idle))
                .setContentTitle(resHelper.getString(R.string.app_name));
    }

    public void start() {
        Timber.v("startForeground()");
        started = true;
        service.startForeground(NOTIFICATION_ID, builder.build());
    }

    void stop() {
        Timber.v("stopForeground()");
        started = false;
        notificationManager.cancel(NOTIFICATION_ID);
        service.stopForeground(true);
    }

    public static Intent getIntent(Context context) {
        return new Intent(context, BlueMusicService.class);
    }

    public static ComponentName startService(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.startForegroundService(intent);
        } else {
            return context.startService(intent);
        }
    }

    private void updateNotification() {
        if (!started) return;
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    void updateActiveDevices(Collection<ManagedDevice> devices) {
        final Iterator<ManagedDevice> iterator = devices.iterator();
        StringBuilder sb = new StringBuilder();
        while (iterator.hasNext()) {
            sb.append(iterator.next().getName());
            if (iterator.hasNext()) sb.append(", ");
        }
        if (!devices.isEmpty()) builder.setContentTitle(sb.toString());
        else builder.setContentTitle(resHelper.getString(R.string.label_no_connected_devices));
        updateNotification();
    }

    void updateMessage(String message) {
        builder.setContentText(message);
        updateNotification();
    }
}
