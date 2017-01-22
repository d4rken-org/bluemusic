package eu.darken.bluemusic.core.bluetooth;

import java.util.Map;

import io.reactivex.Single;

public interface BluetoothSource {
    Single<Map<String, SourceDevice>> getPairedDevices();

    Single<Map<String, SourceDevice>> getConnectedDevices();

    boolean isEnabled();
}
