package eu.darken.bluemusic.bluetooth.core;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import eu.darken.bluemusic.main.core.database.RealmSource;
import eu.darken.bluemusic.main.core.service.ServiceHelper;
import eu.darken.bluemusic.settings.core.Settings;
import eu.darken.mvpbakery.injection.broadcastreceiver.HasManualBroadcastReceiverInjector;
import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;


public class BluetoothEventReceiver extends BroadcastReceiver {
    public static final String EXTRA_DEVICE_EVENT = "eu.darken.bluemusic.core.bluetooth.event";

    @Inject Settings settings;
    @Inject RealmSource realmSource;

    @Override
    public void onReceive(Context context, Intent intent) {
        Timber.v("onReceive(%s, %s)", context, intent);
        ((HasManualBroadcastReceiverInjector) context.getApplicationContext()).broadcastReceiverInjector().inject(this);

        if (!settings.isEnabled()) {
            Timber.i("We are disabled.");
            return;
        }

        final SourceDevice.Event deviceEvent = SourceDevice.Event.createEvent(intent);
        if (deviceEvent == null) {
            Timber.e("Couldn't create device event for %s", intent);
            return;
        }

        final PendingResult goAsync = goAsync();
        Single
                .create((SingleOnSubscribe<Boolean>) emitter -> {
                    final Set<String> managedAddrs = realmSource.getManagedAddresses().blockingGet();
                    if (managedAddrs.contains(deviceEvent.getAddress())) {
                        emitter.onSuccess(true);
                    } else {
                        Timber.d("Event %s belongs to an un-managed device, not gonna bother our service for this", deviceEvent);
                        emitter.onSuccess(false);
                    }
                })
                .subscribeOn(Schedulers.io())
                .timeout(8, TimeUnit.SECONDS)
                .doFinally(goAsync::finish)
                .subscribe((validEvent, throwable) -> {
                    if (throwable != null) {
                        Timber.e(throwable);
                        return;
                    }

                    if (!validEvent) {
                        Timber.w("%s wasn't a valid event.", deviceEvent);
                        return;
                    }

                    Intent service = ServiceHelper.getIntent(context);
                    service.putExtra(EXTRA_DEVICE_EVENT, deviceEvent);
                    final ComponentName componentName = ServiceHelper.startService(context, service);
                    if (componentName != null) Timber.v("Service is already running.");
                });
    }
}
