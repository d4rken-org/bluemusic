package eu.darken.bluemusic.bluetooth.core;

import java.util.Map;

import io.reactivex.Observable;

public interface BluetoothSource {
    int RETRY_COUNT = 300;
    long RETRY_DELAY = 1000;

    Observable<Map<String, SourceDevice>> pairedDevices();

    Observable<Map<String, SourceDevice>> connectedDevices();

    Observable<Boolean> isEnabled();
}
