package eu.darken.bluemusic.bluetooth.core;

import java.util.Map;

import io.reactivex.Observable;

public interface BluetoothSource {
    Observable<Map<String, SourceDevice>> pairedDevices();

    Observable<Map<String, SourceDevice>> connectedDevices();

    Observable<Boolean> isEnabled();
}
