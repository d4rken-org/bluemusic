package eu.darken.bluemusic.core.bluetooth;

import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.Single;

public class FakeDeviceSource implements BluetoothSource {

    @Override
    public Single<Map<String, SourceDevice>> getPairedDevices() {
        return Single.error(new UnsupportedOperationException());
    }

    @Override
    public Single<Map<String, SourceDevice>> getConnectedDevices() {
        return Single.error(new UnsupportedOperationException());
    }

    @Override
    public Observable<Boolean> isEnabled() {
        throw new UnsupportedOperationException();
    }
}
