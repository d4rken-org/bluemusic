package eu.darken.bluemusic.main.core.database;

import android.support.annotation.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import eu.darken.bluemusic.AppComponent;
import eu.darken.bluemusic.bluetooth.core.BluetoothSource;
import eu.darken.bluemusic.bluetooth.core.SourceDevice;
import eu.darken.bluemusic.main.core.audio.AudioStream;
import eu.darken.bluemusic.main.core.audio.StreamHelper;
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
    private final RealmSource realmSource;
    private Observable<Map<String, ManagedDevice>> deviceCache;
    private ObservableEmitter<Map<String, ManagedDevice>> emitter;
    private Disposable enabledSub;


    @Inject
    public DeviceManager(BluetoothSource bluetoothSource, StreamHelper streamHelper, RealmSource realmSource) {
        this.bluetoothSource = bluetoothSource;
        this.streamHelper = streamHelper;
        this.realmSource = realmSource;
    }

    @NonNull
    public Observable<Map<String, ManagedDevice>> observe() {
        synchronized (this) {
            if (deviceCache == null) {
                deviceCache = Observable
                        .create((ObservableOnSubscribe<Map<String, ManagedDevice>>) emitter -> DeviceManager.this.emitter = emitter)
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
                        .refCount()
                        .map(HashMap::new);
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

                                try (Realm realm = realmSource.getNewRealmInstance()) {
                                    final RealmResults<DeviceConfig> deviceConfigs = realm.where(DeviceConfig.class).findAll();

                                    realm.beginTransaction();
                                    for (DeviceConfig config : deviceConfigs) {
                                        if (!paired.containsKey(config.address)) continue;
                                        final SourceDevice sourceDevice = paired.get(config.address);

                                        final ManagedDevice managed = buildDevice(sourceDevice, realm.copyFromRealm(config));
                                        managed.setActive(active.containsKey(managed.getAddress()));

                                        if (active.containsKey(config.address))
                                            config.lastConnected = System.currentTimeMillis();

                                        Timber.v("Loaded: %s", managed);
                                        result.put(managed.getAddress(), managed);
                                    }

                                    realm.commitTransaction();
                                    return result;
                                }
                            });
                })
                .doOnError(Timber::e)
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
                    try (Realm realm = realmSource.getNewRealmInstance()) {
                        realm.beginTransaction();
                        for (ManagedDevice device : devices) {
                            Timber.d("Updated device: %s", device);
                            realm.copyToRealmOrUpdate(device.getDeviceConfig());
                        }
                        realm.commitTransaction();
                        return devices;
                    }
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

                    try (Realm realm = realmSource.getNewRealmInstance()) {
                        realm.beginTransaction();

                        DeviceConfig config = realm.where(DeviceConfig.class).equalTo("address", toAdd.getAddress()).findFirst();
                        if (config != null) {
                            Timber.e("Trying to add already known device: %s (%s)", toAdd, config);
                            throw new IllegalArgumentException();
                        }

                        config = realm.createObject(DeviceConfig.class, toAdd.getAddress());
                        config.musicVolume = streamHelper.getVolumePercentage(toAdd.getStreamId(AudioStream.Type.MUSIC));
                        config.callVolume = null;

                        if (active.containsKey(config.address)) config.lastConnected = System.currentTimeMillis();

                        final ManagedDevice newDevice = buildDevice(toAdd, realm.copyFromRealm(config));
                        newDevice.setActive(active.containsKey(newDevice.getAddress()));

                        Timber.v("Added new device: %s", newDevice);
                        realm.commitTransaction();
                        return newDevice;
                    }
                })
                .doOnError(Timber::e)
                .doOnSuccess(newDevice -> save(Collections.singleton(newDevice)).subscribe());
    }

    public Completable removeDevice(ManagedDevice device) {
        return Completable
                .create(e -> {
                    try (Realm realm = realmSource.getNewRealmInstance()) {
                        DeviceConfig config = realm.where(DeviceConfig.class).equalTo("address", device.getAddress()).findFirst();
                        if (config != null) {
                            realm.beginTransaction();
                            config.deleteFromRealm();
                            realm.commitTransaction();
                        }
                    } finally {
                        Timber.d("Removed %s from managed devices.", device);
                        e.onComplete();
                    }
                })
                .doOnComplete(() -> updateDevices().subscribe());
    }

    ManagedDevice buildDevice(SourceDevice sourceDevice, DeviceConfig config) {
        ManagedDevice managed = new ManagedDevice(sourceDevice, config);
        for (AudioStream.Type type : AudioStream.Type.values()) {
            managed.setMaxVolume(type, streamHelper.getMaxVolume(sourceDevice.getStreamId(type)));
        }
        return managed;
    }
}
