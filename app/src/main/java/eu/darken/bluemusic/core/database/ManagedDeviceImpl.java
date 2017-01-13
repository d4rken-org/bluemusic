package eu.darken.bluemusic.core.database;


import android.bluetooth.BluetoothDevice;

import java.util.Locale;

class ManagedDeviceImpl implements ManagedDevice {
    private final BluetoothDevice bluetoothDevice;
    private final DeviceConfig deviceConfig;

    ManagedDeviceImpl(BluetoothDevice bluetoothDevice, DeviceConfig deviceConfig) {
        this.bluetoothDevice = bluetoothDevice;
        this.deviceConfig = deviceConfig;
    }

    DeviceConfig getDeviceConfig() {
        return deviceConfig;
    }

    @Override
    public void setVolumePercentage(float volumePercentage) {
        deviceConfig.volumePercentage = volumePercentage;
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
        return deviceConfig.volumePercentage;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "Device(address=%s, name=%s, volume=%.2f)", getAddress(), getName(), getVolumePercentage());
    }
}
