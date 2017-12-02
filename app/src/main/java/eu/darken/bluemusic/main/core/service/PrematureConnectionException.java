package eu.darken.bluemusic.main.core.service;


import eu.darken.bluemusic.bluetooth.core.SourceDevice;

class PrematureConnectionException extends Exception {
    PrematureConnectionException(SourceDevice.Event event) {
        super("Device not yet fully connected: " + event);
    }
}
