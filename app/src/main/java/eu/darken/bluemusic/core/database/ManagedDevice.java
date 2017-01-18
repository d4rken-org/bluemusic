package eu.darken.bluemusic.core.database;


import android.support.annotation.NonNull;

import java.util.Locale;

import eu.darken.bluemusic.core.bluetooth.SourceDevice;

public interface ManagedDevice {

    String getName();

    String getAddress();

    float getVolumePercentage();

    void setVolumePercentage(float volumePercentage);

    long getLastConnected();

    void setLastConnected(long timestamp);

    boolean isActive();

    void setActive(boolean active);

    class Action {
        private final ManagedDevice managedDevice;
        private final SourceDevice.Action.Type deviceAction;

        public Action(@NonNull ManagedDevice managedDevice, @NonNull SourceDevice.Action.Type deviceAction) {
            this.managedDevice = managedDevice;
            this.deviceAction = deviceAction;
        }

        @NonNull
        public ManagedDevice getDevice() {
            return managedDevice;
        }

        @NonNull
        public SourceDevice.Action.Type getType() {
            return deviceAction;
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "ManagedDeviceAction(action=%s, device=%s)", deviceAction, managedDevice);
        }
    }
}
