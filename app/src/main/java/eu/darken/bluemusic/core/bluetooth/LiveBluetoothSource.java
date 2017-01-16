package eu.darken.bluemusic.core.bluetooth;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
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
    public Collection<Device> getPairedDevices() {
        Collection<Device> devices = new ArrayList<>();
        for (BluetoothDevice realDevice : manager.getAdapter().getBondedDevices()) {
            if (!BluetoothEventReceiver.isValid(realDevice)) continue;
            devices.add(new DeviceWrapper(realDevice));
        }
        return devices;
    }


    @Override
    public Observable<List<Device>> getConnectedDevices() {
        return Observable.defer(() -> {
            Observable<List<BluetoothDevice>> obsHeadset = LiveBluetoothSource.this.getDevicesForProfile(BluetoothProfile.HEADSET);
            Observable<List<BluetoothDevice>> obsAD2p = LiveBluetoothSource.this.getDevicesForProfile(BluetoothProfile.A2DP);
            return Observable.fromArray(obsHeadset, obsAD2p)
                    .flatMap(obs -> obs.observeOn(Schedulers.computation()))
                    .toList()
                    .map(lists -> {
                        List<Device> devices = new ArrayList<>();
                        for (List<BluetoothDevice> bds : lists) {
                            for (BluetoothDevice bd : bds) {
                                final DeviceWrapper wrapper = new DeviceWrapper(bd);
                                if (devices.contains(wrapper)) continue;
                                devices.add(wrapper);
                            }
                        }
                        return devices;
                    });
        });
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

    private class DeviceWrapper implements Device {
        private final BluetoothDevice realDevice;

        public DeviceWrapper(BluetoothDevice realDevice) {
            this.realDevice = realDevice;
        }

        @Nullable
        @Override
        public String getName() {
            return realDevice.getName();
        }

        @NonNull
        @Override
        public String getAddress() {
            return realDevice.getAddress();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DeviceWrapper that = (DeviceWrapper) o;

            return getAddress().equals(that.getAddress());

        }

        @Override
        public int hashCode() {
            return getAddress().hashCode();
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "Device(name=%s, address=%s)", getName(), getAddress());
        }
    }
}
