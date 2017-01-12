package eu.darken.bluetoothmanager.core.device;


import android.bluetooth.BluetoothDevice;

public class ManagedDeviceImpl implements ManagedDevice {
    private final BluetoothDevice bluetoothDevice;
    private float volumePercentage;

    public ManagedDeviceImpl(BluetoothDevice bluetoothDevice, DeviceConfig deviceConfig) {
        this.bluetoothDevice = bluetoothDevice;
    }

    public void setVolumePercentage(int volumePercentage) {
        this.volumePercentage = volumePercentage;
    }

    @Override
    public String getName() {
        return bluetoothDevice.getName();
    }

    @Override
    public String getAddress() {
        return bluetoothDevice.getAddress();
    }

    @Override
    public float getVolumePercentage() {
        return volumePercentage;
    }
}
