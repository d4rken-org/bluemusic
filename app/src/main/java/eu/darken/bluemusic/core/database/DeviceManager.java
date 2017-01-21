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
    private final BehaviorSubject<Map<String, ManagedDevice>> deviceObs = BehaviorSubject.create();

    public DeviceManager(BluetoothSource bluetoothSource) {
        this.bluetoothSource = bluetoothSource;
    }

    private Realm getRealm() {
        return Realm.getDefaultInstance();
    }

    @NonNull
    public Observable<Map<String, ManagedDevice>> observe() {
        if (!deviceObs.hasValue()) {
            return deviceObs.mergeWith(Observable.defer(() -> load(false)));
        }
        return deviceObs;
    }

    public Observable<Map<String, ManagedDevice>> load(boolean notify) {
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
                        device.setActive(connected.containsKey(device.getAddress()));
                        Timber.v("Loaded: %s", device);
                        devices.put(device.getAddress(), device);
                    }
                    realm.beginTransaction();
                    for (DeviceConfig staleConfig : staleConfigs) staleConfig.deleteFromRealm();
                    realm.commitTransaction();
                    realm.close();
                    return devices;
                })
                .doOnNext(stringManagedDeviceMap -> {
                    if (notify) deviceObs.onNext(stringManagedDeviceMap);
                });
    }

    public void updateVolume(int streamId, float percentage) {
        load(false)
                .subscribeOn(Schedulers.computation())
                .subscribe(deviceMap -> {
                    Timber.d("Updating volume (stream=%d, percentage=%.2f)", streamId, percentage);
                    for (ManagedDevice device : deviceMap.values()) {
                        if (device.isActive()) {
                            device.setVolumePercentage(percentage);
                            Timber.d("Updated device: %s", device);
                            Realm realm = getRealm();
                            realm.beginTransaction();
                            realm.copyToRealmOrUpdate(((ManagedDeviceImpl) device).getDeviceConfig());
                            realm.commitTransaction();
                            realm.close();
                        }
                    }
                    deviceObs.onNext(deviceMap);
                });

    }

}
