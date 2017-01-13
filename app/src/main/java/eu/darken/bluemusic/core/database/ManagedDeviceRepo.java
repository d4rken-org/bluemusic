package eu.darken.bluemusic.core.database;

import android.bluetooth.BluetoothDevice;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import eu.darken.bluemusic.core.bluetooth.BluetoothSource;
import io.realm.Realm;
import io.realm.RealmResults;
import timber.log.Timber;

public class ManagedDeviceRepo {

    private final BluetoothSource bluetoothSource;

    public ManagedDeviceRepo(BluetoothSource bluetoothSource) {
        this.bluetoothSource = bluetoothSource;
    }

    private Realm getRealm() {
        return Realm.getDefaultInstance();
    }

    @NonNull
    public List<ManagedDevice> getDevices() {
        final Map<String, BluetoothDevice> deviceMap = bluetoothSource.getPairedDeviceMap();
        final ArrayList<ManagedDevice> devices = new ArrayList<>();

        Realm realm = getRealm();
        final RealmResults<DeviceConfig> deviceConfigs = realm.where(DeviceConfig.class).findAll();
        for (DeviceConfig _deviceConfig : deviceConfigs) {
            DeviceConfig deviceConfig = realm.copyFromRealm(_deviceConfig);
            ManagedDeviceImpl device = new ManagedDeviceImpl(deviceMap.get(deviceConfig.address), deviceConfig);
            Timber.v("Loaded: %s", device);
            devices.add(device);
        }
        realm.close();
        return devices;
    }

    @Nullable
    public ManagedDevice getDevice(@NonNull String address) {
        for (ManagedDevice managedDevice : getDevices()) if (managedDevice.getAddress().equals(address)) return managedDevice;
        return null;
    }

    public void update(Collection<ManagedDevice> devices) {
        Realm realm = getRealm();
        realm.beginTransaction();
        for (ManagedDevice device : devices) {
            ManagedDeviceImpl _device = (ManagedDeviceImpl) device;
            realm.copyToRealmOrUpdate(_device.getDeviceConfig());
            Timber.v("Stored: %s", device);
        }
        realm.commitTransaction();
        realm.close();
    }

    public ManagedDevice manage(BluetoothDevice knownDevice) {
        Realm realm = getRealm();
        realm.beginTransaction();
        final DeviceConfig object = realm.createObject(DeviceConfig.class);
        object.volumePercentage = -1;
        object.address = knownDevice.getAddress();
        realm.commitTransaction();
        realm.close();
        return new ManagedDeviceImpl(knownDevice, object);
    }

}
