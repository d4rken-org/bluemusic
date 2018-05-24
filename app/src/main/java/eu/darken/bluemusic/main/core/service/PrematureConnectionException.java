package eu.darken.bluemusic.main.core.service;


import eu.darken.bluemusic.bluetooth.core.SourceDevice;

public class PrematureConnectionException extends Exception {
    public PrematureConnectionException(SourceDevice.Event event) {
        super("Device not yet fully connected: " + event);
    }
}
