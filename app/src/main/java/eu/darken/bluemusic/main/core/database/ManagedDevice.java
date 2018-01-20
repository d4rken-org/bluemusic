package eu.darken.bluemusic.main.core.database;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Locale;

import eu.darken.bluemusic.bluetooth.core.SourceDevice;
import eu.darken.bluemusic.main.core.audio.AudioStream;

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

    public boolean isActive() {
        return isActive;
    }

    void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "Device(active=%b, address=%s, name=%s, musicVolume=%.2f, callVolume=%.2f)",
                isActive(), getAddress(), getName(), getVolume(AudioStream.Type.MUSIC), getVolume(AudioStream.Type.CALL));
    }

    public void setMaxVolume(AudioStream.Type type, int max) {
        switch (type) {
            case MUSIC:
                maxMusicVolume = max;
                break;
            case CALL:
                maxCallVolume = max;
                break;
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    public void setVolume(AudioStream.Type type, @Nullable Float volume) {
        switch (type) {
            case MUSIC:
                deviceConfig.musicVolume = volume;
                break;
            case CALL:
                deviceConfig.callVolume = volume;
                break;
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    public Float getVolume(AudioStream.Type type) {
        switch (type) {
            case MUSIC:
                return deviceConfig.musicVolume;
            case CALL:
                return deviceConfig.callVolume;
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    public int getMaxVolume(AudioStream.Type type) {
        switch (type) {
            case MUSIC:
                return maxMusicVolume;
            case CALL:
                return maxCallVolume;
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    public int getRealVolume(AudioStream.Type type) {
        return Math.round(getMaxVolume(type) * getVolume(type));
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

    public AudioStream.Id getStreamId(AudioStream.Type type) {
        return sourceDevice.getStreamId(type);
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
