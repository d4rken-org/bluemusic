package eu.darken.bluemusic.core.bluetooth;

import java.util.Map;

import io.reactivex.Observable;

public interface BluetoothSource {
    Observable<Map<String, SourceDevice>> getPairedDevices();

    Observable<Map<String, SourceDevice>> getConnectedDevices();

}
