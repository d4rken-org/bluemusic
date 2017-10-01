package eu.darken.bluemusic.core.service;

import eu.darken.bluemusic.core.bluetooth.SourceDevice;
import eu.darken.bluemusic.core.database.DeviceManager;
import eu.darken.bluemusic.core.database.ManagedDevice;

public abstract class ActionModule {

    private final DeviceManager deviceManager;

    protected ActionModule(DeviceManager deviceManager) {this.deviceManager = deviceManager;}

    public abstract void handle(ManagedDevice device, SourceDevice.Event event);

    public DeviceManager getDeviceManager() {
        return deviceManager;
    }
}
