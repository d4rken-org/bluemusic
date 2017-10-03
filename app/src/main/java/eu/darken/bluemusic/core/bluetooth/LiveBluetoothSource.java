package eu.darken.bluemusic.core.bluetooth;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

class LiveBluetoothSource implements BluetoothSource {

    private final BluetoothManager manager;
    private final Context context;
    private BluetoothAdapter adapter;

    LiveBluetoothSource(Context context) {
        this.context = context;
        manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();
    }

    @Override
    public Single<Boolean> isEnabled() {
        return Single.create(e -> e.onSuccess(adapter.isEnabled()));
    }

    @Override
    public Single<Map<String, SourceDevice>> getPairedDevices() {
        return Single.defer(() -> Single.just(manager.getAdapter().getBondedDevices()))
                .map(bluetoothDevices -> {
                    Map<String, SourceDevice> devices = new HashMap<>();
                    for (BluetoothDevice realDevice : manager.getAdapter().getBondedDevices()) {
                        final SourceDeviceWrapper deviceWrapper = new SourceDeviceWrapper(realDevice);
                        devices.put(deviceWrapper.getAddress(), deviceWrapper);
                    }
                    Timber.d("Paired devices (%d): %s", devices.size(), devices);
                    return devices;
                });
    }

    @Override
    public Single<Map<String, SourceDevice>> getConnectedDevices() {
        final List<ObservableSource<List<BluetoothDevice>>> devicesProfiles = Arrays.asList(
                LiveBluetoothSource.this.getDevicesForProfile(BluetoothProfile.HEADSET).toObservable(),
                LiveBluetoothSource.this.getDevicesForProfile(BluetoothProfile.GATT).toObservable(),
                LiveBluetoothSource.this.getDevicesForProfile(BluetoothProfile.GATT_SERVER).toObservable(),
                LiveBluetoothSource.this.getDevicesForProfile(BluetoothProfile.HEALTH).toObservable(),
                LiveBluetoothSource.this.getDevicesForProfile(10).toObservable(), // BluetoothProfile.SAP
                LiveBluetoothSource.this.getDevicesForProfile(BluetoothProfile.A2DP).toObservable()
        );

        return Single.defer(() -> Observable.merge(devicesProfiles)
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
                }));
    }

    private Single<List<BluetoothDevice>> getDevicesForProfile(int desiredProfile) {
        return new Single<List<BluetoothDevice>>() {
            @Override
            protected void subscribeActual(SingleObserver<? super List<BluetoothDevice>> observer) {
                BluetoothProfile.ServiceListener mProfileListener = new BluetoothProfile.ServiceListener() {
                    public void onServiceConnected(int profile, BluetoothProfile proxy) {
                        Schedulers.computation().scheduleDirect(() -> {
                            final List<BluetoothDevice> connectedDevices = proxy.getConnectedDevices();
                            Timber.v("onServiceConnected(profile=%d, connected=%s)", profile, connectedDevices);
                            observer.onSuccess(connectedDevices);
                            adapter.closeProfileProxy(profile, proxy);
                        });
                    }

                    public void onServiceDisconnected(int profile) {
                        Timber.d("onServiceDisconnected(%d)", profile);
                    }
                };
                final boolean success = adapter.getProfileProxy(context, mProfileListener, desiredProfile);
                if (!success) observer.onSuccess(new ArrayList<BluetoothDevice>());
            }
        };
    }

}
