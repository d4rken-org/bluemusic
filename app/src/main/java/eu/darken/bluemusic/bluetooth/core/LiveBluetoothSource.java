package eu.darken.bluemusic.bluetooth.core;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.SingleSource;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

class LiveBluetoothSource implements BluetoothSource {

    private final BluetoothManager manager;
    private final Context context;
    @Nullable private final BluetoothAdapter adapter;
    private Observable<Boolean> stateObs;

    LiveBluetoothSource(Context context) {
        this.context = context;
        manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();
        if (adapter == null) Timber.w("BluetoothAdapter is null!");
    }

    @Override
    public Observable<Boolean> isEnabled() {
        synchronized (this) {
            if (stateObs == null) {
                stateObs = Observable.interval(1, TimeUnit.SECONDS)
                        .startWith(0L)
                        .map(count -> {
                            try {
                                return adapter != null && adapter.isEnabled();
                            } catch (SecurityException e) {
                                // Why.... revoked via root?
                                Timber.e(e);
                                return false;
                            }
                        })
                        .filter(new Predicate<Boolean>() {
                            Boolean lastState = null;

                            @Override
                            public boolean test(@NonNull Boolean newState) throws Exception {
                                if (lastState == null || lastState != newState) {
                                    lastState = newState;
                                    return true;
                                }
                                return false;
                            }
                        })
                        .doFinally(() -> {
                            synchronized (LiveBluetoothSource.this) {
                                stateObs = null;
                            }
                        })
                        .replay(1)
                        .refCount();
            }
        }
        return stateObs;
    }

    @Override
    public Single<Map<String, SourceDevice>> getPairedDevices() {
        Timber.v("getPairedDevices()");
        return Single.defer(() -> Single.just(manager.getAdapter().getBondedDevices()))
                .map(bluetoothDevices -> {
                    Map<String, SourceDevice> devices = new HashMap<>();
                    for (BluetoothDevice realDevice : manager.getAdapter().getBondedDevices()) {
                        final SourceDeviceWrapper deviceWrapper = new SourceDeviceWrapper(realDevice);
                        devices.put(deviceWrapper.getAddress(), deviceWrapper);
                    }
                    Timber.d("Paired devices (%d): %s", devices.size(), devices);
                    return devices;
                })
                .subscribeOn(Schedulers.io());
    }

    @Override
    public Single<Map<String, SourceDevice>> getConnectedDevices() {
        Timber.v("getConnectedDevices()");

        final List<SingleSource<List<BluetoothDevice>>> profiles = new ArrayList<>();
        profiles.add(LiveBluetoothSource.this.getDevicesForProfile(BluetoothProfile.HEADSET));
        profiles.add(LiveBluetoothSource.this.getDevicesForProfile(BluetoothProfile.GATT));
        profiles.add(LiveBluetoothSource.this.getDevicesForProfile(BluetoothProfile.GATT_SERVER));
        profiles.add(LiveBluetoothSource.this.getDevicesForProfile(BluetoothProfile.HEALTH));
        profiles.add(LiveBluetoothSource.this.getDevicesForProfile(BluetoothProfile.A2DP));
        return Single.defer(() -> Single.merge(profiles)
                .toList()
                .map(lists -> {
                    HashSet<BluetoothDevice> unique = new HashSet<>();
                    for (List<BluetoothDevice> ll : lists) unique.addAll(ll);
                    return unique;
                })
                .map(combined -> {
                    Timber.d("Connected COMBINED devices (%d): %s", combined.size(), combined);
                    Map<String, SourceDevice> devices = new HashMap<>();
                    for (BluetoothDevice d : combined) devices.put(d.getAddress(), new SourceDeviceWrapper(d));
                    return devices;
                }))
                .subscribeOn(Schedulers.io());
    }

    private Single<List<BluetoothDevice>> getDevicesForProfile(int desiredProfile) {
        return Single
                .create((SingleOnSubscribe<List<BluetoothDevice>>) emitter -> {
                    BluetoothProfile.ServiceListener listener = new BluetoothProfile.ServiceListener() {
                        @SuppressWarnings("ConstantConditions")
                        public void onServiceConnected(int profile, BluetoothProfile proxy) {
                            final List<BluetoothDevice> connectedDevices = proxy.getConnectedDevices();
                            Timber.v("onServiceConnected(profile=%d, connected=%s)", profile, connectedDevices);
                            // Fuck me, listener always calls back on the main thread...
                            emitter.onSuccess(connectedDevices);
                            adapter.closeProfileProxy(profile, proxy);
                        }

                        public void onServiceDisconnected(int profile) {
                            Timber.v("onServiceDisconnected(profile=%d)", profile);
                        }
                    };

                    final boolean success = adapter != null && adapter.getProfileProxy(context, listener, desiredProfile);
                    Timber.v("getDevicesForProfile(profile=%d, success=%b)", desiredProfile, success);
                    if (!success) emitter.onSuccess(new ArrayList<>());
                })
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .timeout(8, TimeUnit.SECONDS, Single.just(new ArrayList<>()));
    }

}
