package eu.darken.bluetoothmanager.backend.known;

import android.bluetooth.BluetoothDevice;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;

public class KnownDeviceRepository {

    private final Realm realm;

    public KnownDeviceRepository() {
        realm = Realm.getDefaultInstance();
    }

    public List<KnownDevice> get() {
        final ArrayList<KnownDevice> devices = new ArrayList<>();
        final RealmResults<KnownDeviceRealm> knownDeviceRealms = realm.where(KnownDeviceRealm.class).findAll();
        for (KnownDeviceRealm knownDeviceRealm : knownDeviceRealms) {

        }
        return devices;
    }

    public void put(BluetoothDevice knownDevice) {
        realm.beginTransaction();
        final KnownDeviceRealm object = realm.createObject(KnownDeviceRealm.class);
        object.setVolumePercentage(0);
        object.setAddress(knownDevice.getAddress());
        realm.commitTransaction();
    }
}
