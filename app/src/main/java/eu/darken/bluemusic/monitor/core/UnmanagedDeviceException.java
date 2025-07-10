package eu.darken.bluemusic.monitor.core;

import eu.darken.bluemusic.bluetooth.core.SourceDevice;

class UnmanagedDeviceException extends Exception {

    UnmanagedDeviceException(SourceDevice.Event deviceEvent) {
        super("Not a managed device: " + deviceEvent.toString());
    }
}
