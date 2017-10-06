package eu.darken.bluemusic.core.service;

import eu.darken.bluemusic.core.bluetooth.SourceDevice;

class UnmanagedDeviceException extends Exception {

    UnmanagedDeviceException(SourceDevice.Event deviceEvent) {
        super("Not a managed device: " + deviceEvent.toString());
    }
}
