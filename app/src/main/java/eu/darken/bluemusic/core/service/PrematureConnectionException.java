package eu.darken.bluemusic.core.service;


import eu.darken.bluemusic.core.bluetooth.SourceDevice;

class PrematureConnectionException extends Exception {
    PrematureConnectionException(SourceDevice.Event event) {
        super("Device not yet fully connected: " + event);
    }
}
