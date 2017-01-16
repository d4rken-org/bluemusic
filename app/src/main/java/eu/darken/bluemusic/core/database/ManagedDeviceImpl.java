package eu.darken.bluemusic.core.database;


import java.util.Locale;

import eu.darken.bluemusic.core.bluetooth.Device;

class ManagedDeviceImpl implements ManagedDevice {
    private final Device device;
    private final DeviceConfig deviceConfig;
    private boolean isActive = true;

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
    public long getLastConnected() {
        return deviceConfig.lastConnected;
    }

    @Override
    public void setLastConnected(long timestamp) {
        deviceConfig.lastConnected = timestamp;
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
    public boolean isActive() {
        return isActive;
    }

    @Override
    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "Device(active=%b, address=%s, name=%s, volume=%.2f)",
                isActive(), getAddress(), getName(), getVolumePercentage());
    }
}
