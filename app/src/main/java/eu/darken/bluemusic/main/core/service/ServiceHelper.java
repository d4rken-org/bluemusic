package eu.darken.bluemusic.main.core.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;

import java.util.Collection;
import java.util.Iterator;

import javax.inject.Inject;

import eu.darken.bluemusic.R;
import eu.darken.bluemusic.ResHelper;
import eu.darken.bluemusic.main.core.database.ManagedDevice;
import eu.darken.bluemusic.main.ui.MainActivity;
import timber.log.Timber;

@BlueMusicServiceComponent.Scope
public class ServiceHelper {

    private final static String NOTIFICATION_CHANNEL_ID = "notification.channel.core";
    private final static int NOTIFICATION_ID = 1;
    static final String STOP_ACTION = "STOP_SERVICE";
    private final NotificationManager notificationManager;
    private final ResHelper resHelper;
    private final NotificationCompat.Builder builder;
    private final Service service;
    private boolean started;
    private long startCall = 0;

    @Inject
    ServiceHelper(BlueMusicService service, NotificationManager notificationManager, ResHelper resHelper) {
        this.service = service;
        this.notificationManager = notificationManager;
        this.resHelper = resHelper;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, resHelper.getString(R.string.label_notification_channel_status), NotificationManager.IMPORTANCE_MIN);
            notificationManager.createNotificationChannel(channel);
        }

        Intent openIntent = new Intent(service, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(service, 0, openIntent, 0);

        Intent stopIntent = new Intent(service, BlueMusicService.class);
        stopIntent.setAction(STOP_ACTION);
        PendingIntent stopPi = PendingIntent.getService(service, 0, stopIntent, 0);

        builder = new NotificationCompat.Builder(service, NOTIFICATION_CHANNEL_ID)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setContentIntent(openPi)
                .setSmallIcon(R.drawable.ic_notification_small)
                .setContentText(resHelper.getString(R.string.label_status_idle))
                .setContentTitle(resHelper.getString(R.string.app_name))
                .addAction(new NotificationCompat.Action.Builder(0, service.getString(R.string.action_exit), stopPi).build());

    }

    synchronized void start() {
        Timber.d("start(started=%b, time=%d, thread=%s)", started, (startCall > 0 ? System.currentTimeMillis() - startCall : 0), Thread.currentThread());
        if (started) {
            Timber.d("Service already launched to foreground.");
            return;
        }
        started = true;
        service.startForeground(NOTIFICATION_ID, builder.build());
        if (startCall == 0) startCall = System.currentTimeMillis();
    }

    synchronized void stop() {
        Timber.d("stop(started=%b, time=%d, thread=%s)", started, (startCall > 0 ? System.currentTimeMillis() - startCall : 0), Thread.currentThread());
        if (!started) {
            // Context.startForegroundService() did not then call Service.startForeground
            // Even if we are below the ANR limit, keep the contract!
            start();
        }
        started = false;
        startCall = 0;
        service.stopForeground(true);
        service.stopSelf();
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

    private synchronized void updateNotification() {
        if (!started) return;
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    void updateActiveDevices(Collection<ManagedDevice> devices) {
        Timber.v("updateActiveDevices(devices=%s)", devices);
        final Iterator<ManagedDevice> iterator = devices.iterator();
        StringBuilder sb = new StringBuilder();
        while (iterator.hasNext()) {
            sb.append(iterator.next().getLabel());
            if (iterator.hasNext()) sb.append(", ");
        }
        if (!devices.isEmpty()) builder.setContentTitle(sb.toString());
        else builder.setContentTitle(resHelper.getString(R.string.label_no_connected_devices));
        updateNotification();
    }

    void updateMessage(String message) {
        Timber.v("updateMessage(message=%s)", message);
        builder.setContentText(message);
        updateNotification();
    }
}
