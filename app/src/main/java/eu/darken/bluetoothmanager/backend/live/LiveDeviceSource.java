package eu.darken.bluetoothmanager.backend.live;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;

import java.util.Collection;

public class LiveDeviceSource implements DeviceSource {

    private final BluetoothAdapter bluetoothAdapter;

    public LiveDeviceSource(Context context) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public Collection<BluetoothDevice> getPairedDevices() {
        return bluetoothAdapter.getBondedDevices();
    }
}
