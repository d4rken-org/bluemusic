package eu.darken.bluemusic.util;

import android.bluetooth.BluetoothClass;
import android.support.annotation.DrawableRes;

import eu.darken.bluemusic.R;
import eu.darken.bluemusic.core.bluetooth.SourceDevice;

public class DeviceHelper {
    @DrawableRes
    public static int getIconForDevice(SourceDevice sourceDevice) {
        int devClass;
        if (sourceDevice.getBluetoothClass() == null) devClass = BluetoothClass.Device.Major.UNCATEGORIZED;
        else devClass = sourceDevice.getBluetoothClass().getMajorDeviceClass();
        if (devClass == BluetoothClass.Device.Major.AUDIO_VIDEO) {
            return R.drawable.ic_headset_white_24dp;
        } else {
            return R.drawable.ic_devices_other_white_24dp;
        }
    }
}
