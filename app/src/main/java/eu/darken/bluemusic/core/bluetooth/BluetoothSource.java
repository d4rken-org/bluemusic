package eu.darken.bluemusic.core.bluetooth;

import java.util.Collection;

public interface BluetoothSource {
    Collection<Device> getPairedDevices();

}
