package eu.darken.bluetoothmanager.core;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import timber.log.Timber;


public class BluetoothEventReceiver extends WakefulBroadcastReceiver {
    public static final String EXTRA_ACTION = "eu.darken.ffs.spotify.bluetooth.action";
    public static final String EXTRA_DEVICE = "eu.darken.ffs.spotify.bluetooth.device";

    @Override
    public void onReceive(Context context, Intent intent) {
        Timber.d("onReceive(%s,%s)", context, intent);
        Timber.d("Device: %s | Action: %s", intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE), intent.getAction());
        Intent service = new Intent(context, EventService.class);
        service.putExtra(EXTRA_ACTION, intent.getAction());
        service.putExtra(EXTRA_DEVICE, (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE));
        startWakefulService(context, service);
    }
}
