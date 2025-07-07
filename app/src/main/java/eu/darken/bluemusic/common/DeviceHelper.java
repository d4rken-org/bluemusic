package eu.darken.bluemusic.common;

import android.bluetooth.BluetoothClass;

import androidx.annotation.DrawableRes;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.bluetooth.core.SourceDevice;
import eu.darken.bluemusic.bluetooth.core.speaker.FakeSpeakerDevice;

public class DeviceHelper {
    @DrawableRes
    public static int getIconForDevice(SourceDevice sourceDevice) {
        if (sourceDevice.getAddress().equals(FakeSpeakerDevice.address)) return R.drawable.ic_android_white_24dp;

        int devClass;
        if (sourceDevice.getBluetoothClass() == null) devClass = BluetoothClass.Device.Major.UNCATEGORIZED;
        else devClass = sourceDevice.getBluetoothClass().getMajorDeviceClass();
        if (devClass == BluetoothClass.Device.Major.AUDIO_VIDEO) {
            return R.drawable.ic_headset_white_24dp;
        } else {
            return R.drawable.ic_devices_other_white_24dp;
        }
    }

    public static String getAliasAndName(SourceDevice sourceDevice) {
        String alias = sourceDevice.getAlias();
        String name = sourceDevice.getName();
        StringBuilder sb = new StringBuilder();
        if (alias != null && !alias.equals(name)) {
            sb.append(alias);
            if (name != null) sb.append(" (").append(name).append(")");
        } else {
            sb.append(name);
        }
        return sb.toString();
    }
}
