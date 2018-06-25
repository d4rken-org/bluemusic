package eu.darken.bluemusic.bluetooth.core;

import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.Single;

public interface BluetoothSource {

    Observable<Map<String, SourceDevice>> pairedDevices();

    Observable<Map<String, SourceDevice>> connectedDevices();

    Observable<Boolean> isEnabled();

    Single<Map<String, SourceDevice>> reloadConnectedDevices();
}
