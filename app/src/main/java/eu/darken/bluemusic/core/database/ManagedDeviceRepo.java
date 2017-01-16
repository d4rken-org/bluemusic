package eu.darken.bluemusic.core.database;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import eu.darken.bluemusic.core.bluetooth.BluetoothSource;
import eu.darken.bluemusic.core.bluetooth.Device;
import eu.darken.bluemusic.util.Tools;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.realm.Realm;
import io.realm.RealmResults;
import timber.log.Timber;


public class ManagedDeviceRepo {

    BluetoothSource bluetoothSource;
    private BehaviorSubject<Collection<ManagedDevice>> deviceObserver;
    private Map<String, ManagedDevice> managedDevices;

    public ManagedDeviceRepo(BluetoothSource bluetoothSource) {
        this.bluetoothSource = bluetoothSource;
    }

    private Realm getRealm() {
        return Realm.getDefaultInstance();
    }

    @NonNull
    public Observable<Collection<ManagedDevice>> getDevices() {
        if (deviceObserver == null) {
            deviceObserver = BehaviorSubject.createDefault(loadManagedDevices().values());
        }
        return deviceObserver;
    }

    private Map<String, ManagedDevice> loadManagedDevices() {
        final Map<String, Device> pairedMap = Tools.toMap(bluetoothSource.getPairedDevices());
        final Map<String, ManagedDevice> devices = new HashMap<>();
        Realm realm = getRealm();
        final RealmResults<DeviceConfig> deviceConfigs = realm.where(DeviceConfig.class).findAll();
        Collection<DeviceConfig> staleConfigs = new ArrayList<>();
        for (DeviceConfig deviceConfig : deviceConfigs) {
            final Device pairedDevice = pairedMap.get(deviceConfig.address);
            if (pairedDevice == null) {
                Timber.d("Stale: %s", deviceConfig.address);
                staleConfigs.add(deviceConfig);
                continue;
            }
            ManagedDeviceImpl device = new ManagedDeviceImpl(pairedDevice, realm.copyFromRealm(deviceConfig));
            Timber.v("Loaded: %s", device);
            devices.put(device.getAddress(), device);
        }
        realm.beginTransaction();
        for (DeviceConfig staleConfig : staleConfigs) staleConfig.deleteFromRealm();
        realm.commitTransaction();
        realm.close();
        return devices;
    }

    public Map<String, ManagedDevice> getManagedDevices() {
        if (managedDevices == null) {
            managedDevices = loadManagedDevices();
        }
        return managedDevices;
    }

    @Nullable
    public ManagedDevice getDevice(String address) {
        ManagedDevice device = getManagedDevices().get(address);
        if (device == null) {
            Timber.d("Unmanaged device, creating new managed device.");

            Device bluetoothDevice = Tools.toMap(bluetoothSource.getPairedDevices()).get(address);
            if (bluetoothDevice == null) return null;

            Realm realm = getRealm();
            final DeviceConfig config = new DeviceConfig();
            config.volumePercentage = -1;
            config.lastConnected = 0;
            config.address = bluetoothDevice.getAddress();
            realm.beginTransaction();
            realm.copyToRealm(config);
            realm.commitTransaction();
            realm.close();
            device = new ManagedDeviceImpl(bluetoothDevice, config);
            managedDevices.put(device.getAddress(), device);
            Timber.i("New managed device: %s", device);
        }
        return device;
    }


    public void updateVolume(int streamId, float percentage) {
        Timber.d("Updating volume (%.2f), updating active devices.", percentage);
        for (ManagedDevice device : getManagedDevices().values()) {
            if (device.isActive()) {
                device.setVolumePercentage(percentage);
            }
        }
        deviceObserver.onNext(getManagedDevices().values());
    }

    public void setInActive(String address) {
//        activeDevices.remove(address);
    }

    public boolean hasActiveDevices() {
        for (ManagedDevice device : managedDevices.values()) {
            if (device.isActive()) return true;
        }
        return false;
    }


}
