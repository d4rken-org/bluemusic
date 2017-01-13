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
    public static final String EXTRA_DEVICE = "eu.darken.bluemusic.core.bluetooth.device";

    @Override
    public void onReceive(Context context, Intent intent) {
        Timber.v("onReceive(%s,%s)", context, intent);
        Timber.v("Device: %s | Action: %s", intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE), intent.getAction());
        Intent service = new Intent(context, BlueMusicService.class);
        service.putExtra(EXTRA_ACTION, intent.getAction());
        service.putExtra(EXTRA_DEVICE, (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE));
        final ComponentName componentName = context.startService(service);
        if (componentName != null) Timber.v("Service is already running.");
    }
}
