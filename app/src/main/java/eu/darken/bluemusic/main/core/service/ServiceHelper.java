package eu.darken.bluemusic.main.core.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import eu.darken.bluemusic.R;
import eu.darken.bluemusic.ResHelper;
import eu.darken.bluemusic.main.core.database.ManagedDevice;
import eu.darken.bluemusic.main.ui.MainActivity;
import eu.darken.bluemusic.util.ValueBox;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

@BlueMusicServiceComponent.Scope
public class ServiceHelper {

    private final static String NOTIFICATION_CHANNEL_ID = "notification.channel.core";
    private final static int NOTIFICATION_ID = 1;
    static final String STOP_ACTION = "STOP_SERVICE";
    private final NotificationManager notificationManager;
    private final ResHelper resHelper;
    private final NotificationCompat.Builder builder;
    private ObservableEmitter<String> emitter;
    private volatile Disposable serviceStopper = Disposables.disposed();
    private volatile boolean isStarted = false;
    private static final String CMD_START = "start";
    private static final String CMD_STOP = "stop";

    @Inject
    ServiceHelper(BlueMusicService service, NotificationManager notificationManager, ResHelper resHelper) {
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

        final ValueBox<String> lastCmd = new ValueBox<>();
        Observable.create((ObservableOnSubscribe<String>) emitter -> ServiceHelper.this.emitter = emitter)
                .doOnNext(cmd -> Timber.v("Submitted: cmd-%s", cmd))
                .filter(cmd -> !cmd.equals(lastCmd.getValue()))
                .doOnNext(lastCmd::setValue)
                .subscribe(cmd -> {
                    Timber.d("Processing cmd-%s", cmd);
                    if (cmd.equals(CMD_START)) {
                        if (!serviceStopper.isDisposed()) {
                            Timber.d("Stopping on-going shutdown due to cmd-%s", cmd);
                            serviceStopper.dispose();
                        }

                        if (isStarted) {
                            Timber.d("Ignoring cmd-%s, already started!", cmd);
                            return;
                        }

                        isStarted = true;
                        Timber.d("Executing startForeground()");
                        service.startForeground(NOTIFICATION_ID, builder.build());
                    } else {
                        if (!isStarted) {
                            Timber.w("Calling stopForeground() without startForeground()");
                            emitter.onNext(CMD_START);
                            emitter.onNext(CMD_STOP);
                            return;
                        }
                        serviceStopper = Completable.timer(1500, TimeUnit.MILLISECONDS)
                                .subscribeOn(Schedulers.io())
                                .doOnComplete(() -> isStarted = false)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(() -> {
                                    Timber.d("Executing stopForeground()");
                                    service.stopForeground(true);
                                    service.stopSelf();
                                    // Sometimes stopForeground doesn't remove the notification, but just makes it removable
                                    notificationManager.cancel(NOTIFICATION_ID);
                                });
                    }
                });
    }

    void start() {
        emitter.onNext(CMD_START);
    }

    void stop() {
        emitter.onNext(CMD_STOP);
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
        if (!isStarted) return;
        Notification notification = builder.build();
        Timber.v("updatingNotification()");
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    void updateActiveDevices(Collection<ManagedDevice> devices) {
        Timber.d("updateActiveDevices(devices=%s)", devices);
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
        Timber.d("updateMessage(message=%s)", message);
        builder.setContentText(message);
        updateNotification();
    }
}
