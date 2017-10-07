package eu.darken.bluemusic.core.database;

import android.support.annotation.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import eu.darken.bluemusic.AppComponent;
import eu.darken.bluemusic.core.bluetooth.BluetoothSource;
import eu.darken.bluemusic.core.bluetooth.SourceDevice;
import eu.darken.bluemusic.core.service.StreamHelper;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import io.realm.Realm;
import io.realm.RealmResults;
import timber.log.Timber;


@AppComponent.Scope
public class DeviceManager {

    private BluetoothSource bluetoothSource;
    private final StreamHelper streamHelper;
    private Observable<Map<String, ManagedDevice>> deviceCache;
    private ObservableEmitter<Map<String, ManagedDevice>> emitter;
    private Disposable enabledSub;


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
        synchronized (this) {
            if (deviceCache == null) {
                deviceCache = Observable
                        .create((ObservableOnSubscribe<Map<String, ManagedDevice>>) emitter -> {
                            DeviceManager.this.emitter = emitter;
                        })
                        .doOnSubscribe(disposable -> {
                            enabledSub = bluetoothSource.isEnabled()
                                    .subscribeOn(Schedulers.io())
                                    .flatMapSingle((Function<Boolean, SingleSource<?>>) active -> updateDevices())
                                    .subscribe();
                        })
                        .doFinally(() -> {
                            enabledSub.dispose();
                            deviceCache = null;
                            emitter = null;
                        })
                        .replay(1)
                        .refCount();
            }
        }
        return deviceCache;
    }

    public Single<Map<String, ManagedDevice>> updateDevices() {
        return bluetoothSource.isEnabled()
                .firstOrError()
                .flatMap((Function<Boolean, SingleSource<Map<String, ManagedDevice>>>) activeBluetooth -> {
                    if (!activeBluetooth) return Single.just(Collections.emptyMap());
                    return Single.zip(
                            bluetoothSource.getConnectedDevices(),
                            bluetoothSource.getPairedDevices(),
                            (active, paired) -> {
                                final Map<String, ManagedDevice> result = new HashMap<>();
                                if (!bluetoothSource.isEnabled().blockingFirst()) return result;

                                Realm realm = getRealm();
                                final RealmResults<DeviceConfig> deviceConfigs = realm.where(DeviceConfig.class).findAll();

                                realm.beginTransaction();
                                for (DeviceConfig config : deviceConfigs) {
                                    if (!paired.containsKey(config.address)) continue;

                                    ManagedDevice managed = new ManagedDevice(paired.get(config.address), realm.copyFromRealm(config));
                                    managed.setMaxMusicVolume(streamHelper.getMaxVolume(streamHelper.getMusicId()));
                                    managed.setMaxCallVolume(streamHelper.getMaxVolume(streamHelper.getCallId()));
                                    managed.setActive(active.containsKey(managed.getAddress()));

                                    if (active.containsKey(config.address)) config.lastConnected = System.currentTimeMillis();

                                    Timber.v("Loaded: %s", managed);
                                    result.put(managed.getAddress(), managed);
                                }

                                realm.commitTransaction();
                                realm.close();
                                return result;
                            });
                })
                .doOnError(throwable -> Timber.e(throwable, null))
                .doOnSuccess(stringManagedDeviceMap -> {
                    synchronized (DeviceManager.this) {
                        if (emitter != null) emitter.onNext(stringManagedDeviceMap);
                    }
                });
    }

    public Single<Map<String, ManagedDevice>> save(Collection<ManagedDevice> toSave) {
        return Single.just(toSave)
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
                })
                .flatMap(managedDevices -> updateDevices());
    }

    public Single<ManagedDevice> addNewDevice(SourceDevice toAdd) {
        return Single.zip(
                bluetoothSource.getConnectedDevices(),
                bluetoothSource.getPairedDevices(),
                (active, paired) -> {
                    if (!paired.containsKey(toAdd.getAddress())) {
                        Timber.e("Device isn't paired device: %s", toAdd);
                        throw new IllegalArgumentException();
                    }

                    try (Realm realm = getRealm()) {
                        realm.beginTransaction();

                        DeviceConfig config = realm.where(DeviceConfig.class).equalTo("address", toAdd.getAddress()).findFirst();
                        if (config != null) {
                            Timber.e("Trying to add already known device: %s (%s)", toAdd, config);
                            throw new IllegalArgumentException();
                        }

                        config = realm.createObject(DeviceConfig.class, toAdd.getAddress());
                        config.musicVolume = streamHelper.getVolumePercentage(streamHelper.getMusicId());
                        config.callVolume = null;

                        if (active.containsKey(config.address)) config.lastConnected = System.currentTimeMillis();

                        ManagedDevice newDevice = new ManagedDevice(toAdd, realm.copyFromRealm(config));
                        newDevice.setMaxMusicVolume(streamHelper.getMaxVolume(streamHelper.getMusicId()));
                        newDevice.setMaxCallVolume(streamHelper.getMaxVolume(streamHelper.getCallId()));
                        newDevice.setActive(active.containsKey(newDevice.getAddress()));
                        Timber.v("Loaded: %s", newDevice);
                        realm.commitTransaction();
                        return newDevice;
                    }
                })
                .doOnError(throwable -> Timber.e(throwable, null))
                .doOnSuccess(newDevice -> save(Collections.singleton(newDevice)).subscribe());
    }

    public Completable removeDevice(ManagedDevice device) {
        return Completable
                .create(e -> {
                    try (Realm realm = getRealm()) {
                        DeviceConfig config = realm.where(DeviceConfig.class).equalTo("address", device.getAddress()).findFirst();
                        if (config != null) {
                            realm.beginTransaction();
                            config.deleteFromRealm();
                            realm.commitTransaction();
                        }
                    } finally {
                        e.onComplete();
                    }
                })
                .doOnComplete(() -> updateDevices().subscribe());
    }
}
