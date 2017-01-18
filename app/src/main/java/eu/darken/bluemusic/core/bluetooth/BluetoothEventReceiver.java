package eu.darken.bluemusic.core.bluetooth;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import eu.darken.bluemusic.core.BlueMusicService;
import timber.log.Timber;


public class BluetoothEventReceiver extends WakefulBroadcastReceiver {
    public static final String EXTRA_DEVICE_ACTION = "eu.darken.bluemusic.core.bluetooth.action";

    @Override
    public void onReceive(Context context, Intent intent) {
        Timber.v("onReceive(%s, %s)", context, intent);

        SourceDevice sourceDevice = new SourceDeviceWrapper((BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE));
        String actionString = intent.getAction();
        Timber.d("Device: %s | Action: %s", sourceDevice, actionString);

        SourceDevice.Action.Type actionType = null;
        if ("android.bluetooth.device.action.ACL_CONNECTED".equals(actionString)) {
            actionType = SourceDevice.Action.Type.CONNECTED;
        } else if ("android.bluetooth.device.action.ACL_DISCONNECTED".equals(actionString)) {
            actionType = SourceDevice.Action.Type.DISCONNECTED;
        }

        if (!isValid(sourceDevice) || actionType == null) {
            Timber.d("Invalid device action!");
            return;
        }
        SourceDevice.Action deviceAction = new SourceDevice.Action(sourceDevice, actionType);

        Intent service = new Intent(context, BlueMusicService.class);
        service.putExtra(EXTRA_DEVICE_ACTION, deviceAction);
        final ComponentName componentName = context.startService(service);
        if (componentName != null) Timber.v("Service is already running.");
    }

    public static boolean isValid(SourceDevice sourceDevice) {
        return sourceDevice.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO;
    }
}
