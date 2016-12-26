package eu.darken.bluetoothmanager.backend.live;

import android.bluetooth.BluetoothDevice;

import java.util.Collection;

public interface DeviceSource {
    Collection<BluetoothDevice> getPairedDevices();
}
