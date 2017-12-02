package eu.darken.bluemusic.main.core.service;

import eu.darken.bluemusic.bluetooth.core.SourceDevice;

class UnmanagedDeviceException extends Exception {

    UnmanagedDeviceException(SourceDevice.Event deviceEvent) {
        super("Not a managed device: " + deviceEvent.toString());
    }
}
