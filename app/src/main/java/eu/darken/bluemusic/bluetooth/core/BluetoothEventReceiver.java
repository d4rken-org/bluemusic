package eu.darken.bluemusic.bluetooth.core;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import javax.inject.Inject;

import eu.darken.bluemusic.main.core.service.ServiceHelper;
import eu.darken.bluemusic.settings.core.Settings;
import eu.darken.ommvplib.injection.broadcastreceiver.HasManualBroadcastReceiverInjector;
import timber.log.Timber;


public class BluetoothEventReceiver extends BroadcastReceiver {
    public static final String EXTRA_DEVICE_EVENT = "eu.darken.bluemusic.core.bluetooth.event";

    @Inject Settings settings;

    @Override
    public void onReceive(Context context, Intent intent) {
        Timber.v("onReceive(%s, %s)", context, intent);
        ((HasManualBroadcastReceiverInjector) context.getApplicationContext()).broadcastReceiverInjector().inject(this);

        if (!settings.isEnabled()) {
            Timber.i("We are disabled.");
            return;
        }

        final BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (bluetoothDevice == null) {
            Timber.w("Intent didn't contain a bluetooth device!");
            return;
        }

        SourceDevice sourceDevice = new SourceDeviceWrapper(bluetoothDevice);

        String actionString = intent.getAction();
        try {
            Timber.d("Device: %s | Action: %s", sourceDevice, actionString);
        } catch (Exception e) {
            Timber.e(e);
            return;
        }

        SourceDevice.Event.Type actionType;
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(actionString)) {
            actionType = SourceDevice.Event.Type.CONNECTED;
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(actionString)) {
            actionType = SourceDevice.Event.Type.DISCONNECTED;
        } else {
            Timber.w("Invalid action: %s", actionString);
            return;
        }

        SourceDevice.Event deviceEvent = new SourceDevice.Event(sourceDevice, actionType);

        Intent service = ServiceHelper.getIntent(context);
        service.putExtra(EXTRA_DEVICE_EVENT, deviceEvent);
        final ComponentName componentName = ServiceHelper.startService(context, service);
        if (componentName != null) Timber.v("Service is already running.");
    }

}
