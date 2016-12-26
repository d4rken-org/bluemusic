package eu.darken.bluetoothmanager.backend.known;


import io.realm.RealmObject;

public class KnownDeviceRealm extends RealmObject implements KnownDevice {


    private String address;
    private int volumePercentage;

    public void setVolumePercentage(int volumePercentage) {
        this.volumePercentage = volumePercentage;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public float getVolumePercentage() {
        return volumePercentage;
    }
}
