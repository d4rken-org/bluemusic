package eu.darken.bluemusic.main.core.service;


import eu.darken.bluemusic.bluetooth.core.SourceDevice;

public class MissingDeviceException extends Exception {
    public MissingDeviceException(SourceDevice.Event event) {
        super("Device not yet fully connected: " + event);
    }
}
