package eu.darken.bluetoothmanager.core.manager;

import android.bluetooth.BluetoothDevice;

import java.util.Collection;
import java.util.Map;

public interface BluetoothSource {
    Collection<BluetoothDevice> getPairedDevices();

    Map<String, BluetoothDevice> getPairedDeviceMap();
}
