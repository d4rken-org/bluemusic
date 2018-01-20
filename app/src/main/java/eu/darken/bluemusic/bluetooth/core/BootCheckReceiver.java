package eu.darken.bluemusic.bluetooth.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import eu.darken.bluemusic.settings.core.Settings;
import eu.darken.ommvplib.injection.broadcastreceiver.HasManualBroadcastReceiverInjector;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

public class BootCheckReceiver extends BroadcastReceiver {

    @Inject Settings settings;
    @Inject BluetoothSource bluetoothSource;
    @Inject EventGenerator eventGenerator;

    @Override
    public void onReceive(Context context, Intent intent) {
        Timber.v("onReceive(%s, %s)", context, intent);
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Timber.e("Triggered with unknown intent: %s", intent);
            return;
        }

        ((HasManualBroadcastReceiverInjector) context.getApplicationContext()).broadcastReceiverInjector().inject(this);

        if (!settings.isEnabled()) {
            Timber.i("We are disabled.");
            return;
        }
        if (!settings.isBootRestoreEnabled()) {
            Timber.i("Restoring on boot is disabled.");
            return;
        }

        Timber.d("We were rebooted, let's see if any Bluetooth device is connected...");
        final PendingResult pendingResult = goAsync();
        bluetoothSource.getConnectedDevices()
                .subscribeOn(Schedulers.io())
                .map(Map::values)
                .map(ArrayList::new)
                .timeout(8, TimeUnit.SECONDS, Single.just(new ArrayList<>()))
                .doFinally(pendingResult::finish)
                .subscribe(devices -> {
                    Timber.i("Connected devices: %s", devices);
                    if (devices.size() > 0) {
                        eventGenerator.send(devices.get(0), SourceDevice.Event.Type.CONNECTED);
                    }
                }, Timber::w);
    }
}
