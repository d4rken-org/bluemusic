package eu.darken.bluetoothmanager.core.device;

import android.bluetooth.BluetoothDevice;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.darken.bluetoothmanager.core.manager.BluetoothSource;
import io.realm.Realm;
import io.realm.RealmResults;

public class ManagedDeviceRepo {

    private final BluetoothSource bluetoothSource;

    public ManagedDeviceRepo(BluetoothSource bluetoothSource) {
        this.bluetoothSource = bluetoothSource;
    }

    private Realm getRealm() {
        return Realm.getDefaultInstance();
    }

    @NonNull
    public List<ManagedDevice> get() {
        final Map<String, BluetoothDevice> deviceMap = bluetoothSource.getPairedDeviceMap();
        final ArrayList<ManagedDevice> devices = new ArrayList<>();

        Realm realm = getRealm();
        final RealmResults<DeviceConfig> deviceConfigs = realm.where(DeviceConfig.class).findAll();
        for (DeviceConfig deviceConfig : deviceConfigs) {
            ManagedDeviceImpl knownDevice = new ManagedDeviceImpl(deviceMap.get(deviceConfig.address), deviceConfig);
            devices.add(knownDevice);
        }
        realm.close();
        return devices;
    }

    @NonNull
    public Map<String, ManagedDevice> getMap() {
        Map<String, ManagedDevice> map = new HashMap<>();
        for (ManagedDevice managedDevice : get()) map.put(managedDevice.getAddress(), managedDevice);
        return map;
    }

    public void update(ManagedDevice device) {
        Realm realm = getRealm();
        realm.beginTransaction();
        DeviceConfig deviceConfig = realm.where(DeviceConfig.class)
                .equalTo("address", device.getAddress()).findFirst();
        deviceConfig.update(device);
        realm.commitTransaction();
        realm.close();
    }

    public void put(BluetoothDevice knownDevice) {
        Realm realm = getRealm();
        realm.beginTransaction();
        final DeviceConfig object = realm.createObject(DeviceConfig.class);
        object.volumePercentage = 0;
        object.address = knownDevice.getAddress();
        realm.commitTransaction();
        realm.close();
    }
}
