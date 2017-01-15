package eu.darken.bluemusic.core.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import eu.darken.bluemusic.core.BlueMusicService;
import timber.log.Timber;


public class BluetoothEventReceiver extends WakefulBroadcastReceiver {
    public static final String EXTRA_ACTION = "eu.darken.bluemusic.core.bluetooth.action";
    public static final String EXTRA_DEVICE_ADDRESS = "eu.darken.bluemusic.core.bluetooth.device.address";

    @Override
    public void onReceive(Context context, Intent intent) {
        Timber.v("onReceive(%s,%s)", context, intent);
        String action = intent.getAction();
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        String address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS);
        Timber.v("Device: %s | Action: %s", device, action);

        if (device == null && address == null) {
            Timber.e("Unknown intent!");
            return;
        }

        Intent service = new Intent(context, BlueMusicService.class);
        service.putExtra(EXTRA_ACTION, action);
        service.putExtra(EXTRA_DEVICE_ADDRESS, device != null ? device.getAddress() : address);
        final ComponentName componentName = context.startService(service);
        if (componentName != null) Timber.v("Service is already running.");
    }
}
