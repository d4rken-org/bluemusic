package eu.darken.bluemusic.bluetooth.core;

import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.Single;

public interface BluetoothSource {
    Single<Map<String, SourceDevice>> getPairedDevices();

    Single<Map<String, SourceDevice>> getConnectedDevices();

    Observable<Boolean> isEnabled();
}
