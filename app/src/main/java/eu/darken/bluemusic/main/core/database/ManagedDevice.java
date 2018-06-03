package eu.darken.bluemusic.main.core.database;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import eu.darken.bluemusic.bluetooth.core.SourceDevice;
import eu.darken.bluemusic.main.core.audio.AudioStream;
import timber.log.Timber;

public class ManagedDevice {

    private final SourceDevice sourceDevice;
    private final DeviceConfig deviceConfig;
    private boolean isActive;
    private Map<AudioStream.Type, Integer> maxVolumeMap = new HashMap<>();

    ManagedDevice(SourceDevice sourceDevice, DeviceConfig deviceConfig) {
        this.sourceDevice = sourceDevice;
        this.deviceConfig = deviceConfig;
    }

    public String getLabel() {
        return sourceDevice.getLabel();
    }

    @Nullable
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
        return String.format(Locale.US, "Device(active=%b, address=%s, name=%s, musicVolume=%.2f, callVolume=%.2f, ringVolume=%.2f)",
                isActive(), getAddress(), getName(), getVolume(AudioStream.Type.MUSIC), getVolume(AudioStream.Type.CALL), getVolume(AudioStream.Type.RINGTONE));
    }

    public void setMaxVolume(AudioStream.Type type, int max) {
        maxVolumeMap.put(type, max);
    }

    public int getMaxVolume(AudioStream.Type type) {
        return maxVolumeMap.get(type);
    }

    public void setVolume(AudioStream.Type type, @Nullable Float volume) {
        switch (type) {
            case MUSIC:
                deviceConfig.musicVolume = volume;
                break;
            case CALL:
                deviceConfig.callVolume = volume;
                break;
            case RINGTONE:
                deviceConfig.ringVolume = volume;
                break;
            case NOTIFICATION:
                deviceConfig.notificationVolume = volume;
                break;
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    @Nullable
    public Float getVolume(AudioStream.Type type) {
        switch (type) {
            case MUSIC:
                return deviceConfig.musicVolume;
            case CALL:
                return deviceConfig.callVolume;
            case RINGTONE:
                return deviceConfig.ringVolume;
            case NOTIFICATION:
                return deviceConfig.notificationVolume;
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

    @Nullable
    public String getLaunchPkg() {
        return deviceConfig.launchPkg;
    }

    public void setLaunchPkg(@Nullable String pkg) {
        deviceConfig.launchPkg = pkg;
    }

    public void setAutoPlayEnabled(boolean enabled) {
        deviceConfig.autoplay = enabled;
    }

    public AudioStream.Id getStreamId(AudioStream.Type type) {
        return sourceDevice.getStreamId(type);
    }

    /**
     * @return NULL if no mapping exists for this device
     */
    @Nullable
    public AudioStream.Type getStreamType(AudioStream.Id id) {
        for (AudioStream.Type type : AudioStream.Type.values()) {
            if (getStreamId(type) == id) return type;
        }
        Timber.d("%s is not mapped by %s.", id, getLabel());
        return null;
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
