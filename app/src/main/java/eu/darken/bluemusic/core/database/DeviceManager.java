package eu.darken.bluemusic.core.database;

import android.support.annotation.NonNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import eu.darken.bluemusic.core.bluetooth.BluetoothSource;
import eu.darken.bluemusic.core.bluetooth.SourceDevice;
import eu.darken.bluemusic.core.service.StreamHelper;
import eu.darken.bluemusic.util.dagger.ApplicationScope;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import io.realm.Realm;
import io.realm.RealmResults;
import timber.log.Timber;


@ApplicationScope
public class DeviceManager {

    private BluetoothSource bluetoothSource;
    private final StreamHelper streamHelper;
    private final BehaviorSubject<Map<String, ManagedDevice>> deviceObs = BehaviorSubject.create();

    @Inject
    public DeviceManager(BluetoothSource bluetoothSource, StreamHelper streamHelper) {
        this.bluetoothSource = bluetoothSource;
        this.streamHelper = streamHelper;
    }

    private Realm getRealm() {
        return Realm.getDefaultInstance();
    }

    @NonNull
    public Observable<Map<String, ManagedDevice>> observe() {
        Observable.defer(() -> load(true).toObservable())
                .subscribeOn(Schedulers.computation())
                .subscribe();
        return deviceObs;
    }

    public Single<Map<String, ManagedDevice>> load(boolean notify) {
        return Single.zip(
                bluetoothSource.getConnectedDevices(),
                bluetoothSource.getPairedDevices(),
                (active, paired) -> {
                    final Map<String, ManagedDevice> result = new HashMap<>();
                    Realm realm = getRealm();
                    realm.beginTransaction();
                    for (SourceDevice dev : paired.values()) {
                        DeviceConfig config = realm.where(DeviceConfig.class).equalTo("address", dev.getAddress()).findFirst();
                        if (config == null) {
                            config = realm.createObject(DeviceConfig.class, dev.getAddress());
                            config.musicVolume = streamHelper.getVolumePercentage(streamHelper.getMusicId());
                            config.voiceVolume = streamHelper.getVolumePercentage(streamHelper.getVoiceId());
                        }

                        if (active.containsKey(config.address)) config.lastConnected = System.currentTimeMillis();

                        ManagedDevice managed = new ManagedDevice(dev, realm.copyFromRealm(config));
                        managed.setMaxMusicVolume(streamHelper.getMaxVolume(streamHelper.getMusicId()));
                        managed.setMaxVoiceVolume(streamHelper.getMaxVolume(streamHelper.getVoiceId()));
                        managed.setActive(active.containsKey(managed.getAddress()));
                        Timber.v("Loaded: %s", managed);
                        result.put(managed.getAddress(), managed);
                    }

                    if (bluetoothSource.isEnabled()) {
                        final RealmResults<DeviceConfig> all = realm.where(DeviceConfig.class).findAll();
                        for (DeviceConfig config : all) {
                            if (!result.containsKey(config.address)) {
                                Timber.i("Deleted stale config from %s", config.address);
                                config.deleteFromRealm();
                            }
                        }
                    }
                    realm.commitTransaction();
                    realm.close();
                    return result;
                })
                .doOnError(throwable -> Timber.e(throwable, null))
                .doOnSuccess(stringManagedDeviceMap -> {
                    if (notify) deviceObs.onNext(stringManagedDeviceMap);
                });
    }

    public Observable<Collection<ManagedDevice>> update(Collection<ManagedDevice> managedDevice) {
        return Observable.just(managedDevice)
                .subscribeOn(Schedulers.computation())
                .map(devices -> {
                    Realm realm = getRealm();
                    realm.beginTransaction();
                    for (ManagedDevice device : devices) {
                        Timber.d("Updated device: %s", device);
                        realm.copyToRealmOrUpdate(device.getDeviceConfig());
                    }
                    realm.commitTransaction();
                    realm.close();
                    return devices;
                });
    }

    public void notifyChanged(Collection<ManagedDevice> updatedDevs) {
        Map<String, ManagedDevice> updatedMap = deviceObs.getValue();
        if (updatedMap == null) updatedMap = new HashMap<>();
        for (ManagedDevice d : updatedDevs) {
            updatedMap.put(d.getAddress(), d);
        }
        deviceObs.onNext(updatedMap);
    }
}
