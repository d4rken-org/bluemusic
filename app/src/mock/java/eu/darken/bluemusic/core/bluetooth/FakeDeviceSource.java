package eu.darken.bluemusic.core.bluetooth;

import android.content.Context;

import java.util.Map;

import io.reactivex.Single;

public class FakeDeviceSource implements BluetoothSource {

    private final Context context;

    public FakeDeviceSource(Context context) {
        this.context = context;
    }

    @Override
    public Single<Map<String, SourceDevice>> getPairedDevices() {
        return Single.error(new UnsupportedOperationException());
    }

    @Override
    public Single<Map<String, SourceDevice>> getConnectedDevices() {
        return Single.error(new UnsupportedOperationException());
    }

    @Override
    public boolean isEnabled() {
        throw new UnsupportedOperationException();
//        return false;
    }
}
