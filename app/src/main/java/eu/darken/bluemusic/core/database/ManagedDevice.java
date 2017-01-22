package eu.darken.bluemusic.core.database;


import android.support.annotation.NonNull;

import java.util.Locale;

import eu.darken.bluemusic.core.bluetooth.SourceDevice;
import eu.darken.bluemusic.core.service.StreamHelper;

public class ManagedDevice {

    private final SourceDevice sourceDevice;
    private final DeviceConfig deviceConfig;
    private boolean isActive;
    private int maxMusicVolume;
    private int maxVoiceVolume;
    private StreamHelper volumes;

    ManagedDevice(SourceDevice sourceDevice, DeviceConfig deviceConfig) {
        this.sourceDevice = sourceDevice;
        this.deviceConfig = deviceConfig;
    }

    DeviceConfig getDeviceConfig() {
        return deviceConfig;
    }

    public void setMusicVolume(float musicVolume) {
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

    String getAddress() {
        return sourceDevice.getAddress();
    }

    public float getMusicVolume() {
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
        return String.format(Locale.US, "Device(active=%b, address=%s, name=%s, musicVolume=%.2f, voiceVolume=%.2f)",
                isActive(), getAddress(), getName(), getMusicVolume(), getVoiceVolume());
    }

    public int getMaxMusicVolume() {
        return maxMusicVolume;
    }

    void setMaxMusicVolume(int maxMusicVolume) {
        this.maxMusicVolume = maxMusicVolume;
    }

    public int getMaxVoiceVolume() {
        return maxVoiceVolume;
    }

    void setMaxVoiceVolume(int maxVoiceVolume) {
        this.maxVoiceVolume = maxVoiceVolume;
    }

    public void setVoiceVolume(float voiceVolume) {
        deviceConfig.voiceVolume = voiceVolume;
    }

    public float getVoiceVolume() {
        return deviceConfig.voiceVolume;
    }

    public int getRealMusicVolume() {
        return Math.round(getMaxMusicVolume() * getMusicVolume());
    }

    public int getRealVoiceVolume() {
        return Math.round(getMaxVoiceVolume() * getVoiceVolume());
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
