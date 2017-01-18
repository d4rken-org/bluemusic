package eu.darken.bluemusic.core.bluetooth;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import timber.log.Timber;

public class LiveBluetoothSource implements BluetoothSource {

    private final BluetoothManager manager;
    private final Context context;
    private BluetoothAdapter adapter;

    public LiveBluetoothSource(Context context) {
        this.context = context;
        manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();
    }

    @Override
    public Observable<Map<String, SourceDevice>> getPairedDevices() {
        return Observable.defer(() -> Observable.just(manager.getAdapter().getBondedDevices()))
                .map(bluetoothDevices -> {
                    Map<String, SourceDevice> devices = new HashMap<>();
                    for (BluetoothDevice realDevice : manager.getAdapter().getBondedDevices()) {
                        final SourceDeviceWrapper deviceWrapper = new SourceDeviceWrapper(realDevice);
                        if (!BluetoothEventReceiver.isValid(deviceWrapper)) continue;
                        devices.put(deviceWrapper.getAddress(), deviceWrapper);
                    }
                    Timber.d("Paired devices (%d): %s", devices.size(), devices);
                    return devices;
                });
    }


    @Override
    public Observable<Map<String, SourceDevice>> getConnectedDevices() {
        return Observable.defer(() -> Observable.zip(
                LiveBluetoothSource.this.getDevicesForProfile(BluetoothProfile.HEADSET),
                LiveBluetoothSource.this.getDevicesForProfile(BluetoothProfile.A2DP),
                (headSets, a2dps) -> {
                    Set<BluetoothDevice> combined = new HashSet<>(headSets);
                    combined.addAll(a2dps);
                    Timber.d("Connected HEADSET devices (%d): %s", headSets.size(), headSets);
                    Timber.d("Connected A2DP devices (%d): %s", a2dps.size(), a2dps);
                    Timber.d("Connected COMBINED devices (%d): %s", combined.size(), combined);
                    return combined;
                })
                .map(bluetoothDevices -> {
                    Map<String, SourceDevice> devices = new HashMap<>();
                    for (BluetoothDevice d : bluetoothDevices) devices.put(d.getAddress(), new SourceDeviceWrapper(d));
                    return devices;
                }));
    }

    private Observable<List<BluetoothDevice>> getDevicesForProfile(int desiredProfile) {
        PublishSubject<List<BluetoothDevice>> observable = PublishSubject.create();
        BluetoothProfile.ServiceListener mProfileListener = new BluetoothProfile.ServiceListener() {
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                Timber.d("onServiceConnected(profile=%d, proxy=%s)", profile, proxy);
                observable.onNext(proxy.getConnectedDevices());
                observable.onComplete();
                adapter.closeProfileProxy(profile, proxy);
            }

            public void onServiceDisconnected(int profile) {
                Timber.d("onServiceDisconnected(%d)", profile);
            }
        };
        adapter.getProfileProxy(context, mProfileListener, desiredProfile);
        return observable;
    }

}
