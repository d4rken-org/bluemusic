package eu.darken.bluetoothmanager.core.device;


import io.realm.RealmObject;

public class DeviceConfig extends RealmObject {
    String address;
    float volumePercentage;

    public void update(ManagedDevice device) {
        volumePercentage = device.getVolumePercentage();
    }
}
