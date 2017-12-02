package eu.darken.bluemusic.main.core.database;


import android.support.annotation.NonNull;

import java.util.Locale;

import eu.darken.bluemusic.bluetooth.core.SourceDevice;

public class ManagedDevice {

    private final SourceDevice sourceDevice;
    private final DeviceConfig deviceConfig;
    private boolean isActive;
    private int maxMusicVolume;
    private int maxCallVolume;

    ManagedDevice(SourceDevice sourceDevice, DeviceConfig deviceConfig) {
        this.sourceDevice = sourceDevice;
        this.deviceConfig = deviceConfig;
    }

    public String tryGetAlias() {
        String alias = getAlias();
        return alias != null ? alias : getName();
    }

    public String getAlias() {
        return sourceDevice.getAlias();
    }

    public boolean setAlias(String newAlias) {
        return sourceDevice.setAlias(newAlias);
    }

    public SourceDevice getSourceDevice() {
        return sourceDevice;
    }

    DeviceConfig getDeviceConfig() {
        return deviceConfig;
    }

    public void setMusicVolume(Float musicVolume) {
        deviceConfig.musicVolume = musicVolume;
    }

    public long getLastConnected() {
        return deviceConfig.lastConnected;
    }

    public void setLastConnected(long timestamp) {
        deviceConfig.lastConnected = timestamp;
    }

    public String getName() {
        return sourceDevice.getName();
    }

    public String getAddress() {
        return sourceDevice.getAddress();
    }

    public Float getMusicVolume() {
        return deviceConfig.musicVolume;
    }

    public boolean isActive() {
        return isActive;
    }

    void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "Device(active=%b, address=%s, name=%s, musicVolume=%.2f, callVolume=%.2f)",
                isActive(), getAddress(), getName(), getMusicVolume(), getCallVolume());
    }

    public int getMaxMusicVolume() {
        return maxMusicVolume;
    }

    void setMaxMusicVolume(int maxMusicVolume) {
        this.maxMusicVolume = maxMusicVolume;
    }

    public int getMaxCallVolume() {
        return maxCallVolume;
    }

    void setMaxCallVolume(int maxCallVolume) {
        this.maxCallVolume = maxCallVolume;
    }

    public void setCallVolume(Float callVolume) {
        deviceConfig.callVolume = callVolume;
    }

    public Float getCallVolume() {
        return deviceConfig.callVolume;
    }

    public int getRealMusicVolume() {
        return Math.round(getMaxMusicVolume() * getMusicVolume());
    }

    public int getRealCallVolume() {
        return Math.round(getMaxCallVolume() * getCallVolume());
    }

    public void setActionDelay(Long actionDelay) {
        deviceConfig.actionDelay = actionDelay;
    }

    public Long getActionDelay() {
        return deviceConfig.actionDelay;
    }

    public void setAdjustmentDelay(Long adjustmentDelay) {
        deviceConfig.adjustmentDelay = adjustmentDelay;
    }

    public Long getAdjustmentDelay() {
        return deviceConfig.adjustmentDelay;
    }

    public boolean isAutoPlayEnabled() {
        return deviceConfig.autoplay;
    }

    public void setAutoPlayEnabled(boolean enabled) {
        deviceConfig.autoplay = enabled;
    }

    public static class Action {
        private final ManagedDevice managedDevice;
        private final SourceDevice.Event.Type deviceAction;

        public Action(@NonNull ManagedDevice managedDevice, @NonNull SourceDevice.Event.Type deviceAction) {
            this.managedDevice = managedDevice;
            this.deviceAction = deviceAction;
        }

        @NonNull
        public ManagedDevice getDevice() {
            return managedDevice;
        }

        @NonNull
        public SourceDevice.Event.Type getType() {
            return deviceAction;
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "ManagedDeviceAction(action=%s, device=%s)", deviceAction, managedDevice);
        }
    }
}
