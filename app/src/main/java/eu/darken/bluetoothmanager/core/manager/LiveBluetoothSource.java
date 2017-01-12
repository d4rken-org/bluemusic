package eu.darken.bluetoothmanager.core.manager;


import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class LiveBluetoothSource implements BluetoothSource {

    private final BluetoothManager manager;

    public LiveBluetoothSource(Context context) {
        manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    }

    public Collection<BluetoothDevice> getPairedDevices() {
        return manager.getAdapter().getBondedDevices();
    }

    @Override
    public Map<String, BluetoothDevice> getPairedDeviceMap() {
        Map<String, BluetoothDevice> deviceMap = new HashMap<>();
        for (BluetoothDevice device : manager.getAdapter().getBondedDevices()) {
            deviceMap.put(device.getAddress(), device);
        }
        return deviceMap;
    }
}
