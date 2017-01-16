package eu.darken.bluemusic.core.bluetooth;

import java.util.Collection;
import java.util.List;

import io.reactivex.Observable;

public interface BluetoothSource {
    Collection<Device> getPairedDevices();

    Observable<List<Device>> getConnectedDevices();

}
