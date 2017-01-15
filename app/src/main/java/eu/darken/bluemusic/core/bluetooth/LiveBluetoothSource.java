package eu.darken.bluemusic.core.bluetooth;


import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;

public class LiveBluetoothSource implements BluetoothSource {

    private final BluetoothManager manager;

    public LiveBluetoothSource(Context context) {
        manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    }

    public Collection<Device> getPairedDevices() {
        Collection<Device> devices = new ArrayList<>();
        for (BluetoothDevice realDevice : manager.getAdapter().getBondedDevices()) {
            devices.add(new DeviceWrapper(realDevice));
        }
        return devices;
    }

    private class DeviceWrapper implements Device {
        private final BluetoothDevice realDevice;

        public DeviceWrapper(BluetoothDevice realDevice) {
            this.realDevice = realDevice;
        }

        @Nullable
        @Override
        public String getName() {
            return realDevice.getName();
        }

        @Override
        public String getAddress() {
            return realDevice.getAddress();
        }
    }
}
