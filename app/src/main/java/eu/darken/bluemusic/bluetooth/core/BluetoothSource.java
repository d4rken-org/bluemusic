package eu.darken.bluemusic.bluetooth.core;

import java.util.Map;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

public interface BluetoothSource {

    Observable<Map<String, SourceDevice>> pairedDevices();

    Observable<Map<String, SourceDevice>> connectedDevices();

    Observable<Boolean> isEnabled();

    Single<Map<String, SourceDevice>> reloadConnectedDevices();
}
