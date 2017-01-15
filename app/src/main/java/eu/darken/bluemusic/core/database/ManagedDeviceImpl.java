package eu.darken.bluemusic.core.database;


import java.util.Locale;

import eu.darken.bluemusic.core.bluetooth.Device;

class ManagedDeviceImpl implements ManagedDevice {
    private final Device device;
    private final DeviceConfig deviceConfig;

    ManagedDeviceImpl(Device device, DeviceConfig deviceConfig) {
        this.device = device;
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
        return device.getName();
    }

    @Override
    public String getAddress() {
        return device.getAddress();
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
