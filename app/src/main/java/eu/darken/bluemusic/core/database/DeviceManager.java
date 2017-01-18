package eu.darken.bluemusic.core.database;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import eu.darken.bluemusic.core.bluetooth.BluetoothSource;
import eu.darken.bluemusic.core.bluetooth.SourceDevice;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import io.realm.Realm;
import io.realm.RealmResults;
import timber.log.Timber;


public class DeviceManager {

    private BluetoothSource bluetoothSource;
    private BehaviorSubject<Map<String, ManagedDevice>> deviceObserver;

    public DeviceManager(BluetoothSource bluetoothSource) {
        this.bluetoothSource = bluetoothSource;
    }

    private Realm getRealm() {
        return Realm.getDefaultInstance();
    }

    @NonNull
    public synchronized Observable<Map<String, ManagedDevice>> getDevices() {
        if (deviceObserver == null) {
            deviceObserver = BehaviorSubject.create();
            Observable.defer(this::loadManagedDevices)
                    .subscribeOn(Schedulers.computation())
                    .subscribe(deviceMap -> deviceObserver.onNext(deviceMap));
        }
        return deviceObserver;
    }

    public synchronized Observable<Map<String, ManagedDevice>> loadManagedDevices() {
        return Observable.zip(
                bluetoothSource.getConnectedDevices(),
                bluetoothSource.getPairedDevices(),
                (connected, paired) -> {
                    final Map<String, ManagedDevice> devices = new HashMap<>();
                    Realm realm = getRealm();
                    final RealmResults<DeviceConfig> deviceConfigs = realm.where(DeviceConfig.class).findAll();
                    Collection<DeviceConfig> staleConfigs = new ArrayList<>();
                    for (DeviceConfig deviceConfig : deviceConfigs) {
                        final SourceDevice pairedSourceDevice = paired.get(deviceConfig.address);
                        if (pairedSourceDevice == null) {
                            Timber.d("Stale: %s", deviceConfig.address);
                            staleConfigs.add(deviceConfig);
                            continue;
                        }
                        ManagedDeviceImpl device = new ManagedDeviceImpl(pairedSourceDevice, realm.copyFromRealm(deviceConfig));
                        device.setActive(paired.containsKey(device.getAddress()));
                        Timber.v("Loaded: %s", device);
                        devices.put(device.getAddress(), device);
                    }
                    realm.beginTransaction();
                    for (DeviceConfig staleConfig : staleConfigs) staleConfig.deleteFromRealm();
                    realm.commitTransaction();
                    realm.close();
                    return devices;
                });
    }

    public void updateVolume(int streamId, float percentage) {
        Timber.d("Updating volume (%.2f), updating active devices.", percentage);
//        for (ManagedDevice device : getDevices().blockingFirst().values()) {
//            if (device.isActive()) {
//                device.setVolumePercentage(percentage);
//            }
//        }
    }

    public Observable<Boolean> hasActiveDevices() {
        return null;
    }

}
