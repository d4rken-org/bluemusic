package eu.darken.bluemusic.bluetooth.core;


import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelUuid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import androidx.core.app.ActivityCompat;
import eu.darken.bluemusic.main.core.database.RealmSource;
import eu.darken.bluemusic.main.core.service.MissingDeviceException;
import eu.darken.bluemusic.settings.core.Settings;
import eu.darken.bluemusic.util.ApiHelper;
import eu.darken.bluemusic.util.Check;
import eu.darken.bluemusic.util.ui.RetryWithDelay;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleOnSubscribe;
import io.reactivex.rxjava3.core.SingleSource;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import timber.log.Timber;

class LiveBluetoothSource implements BluetoothSource {

    private final Settings settings;
    private final RealmSource realmSource;
    private final Context context;

    private final SourceDevice fakeSpeakerDevice;
    private final BehaviorSubject<Map<String, SourceDevice>> pairedPublisher = BehaviorSubject.create();
    private final BehaviorSubject<Map<String, SourceDevice>> connectedPublisher = BehaviorSubject.create();
    private final BehaviorSubject<Boolean> adapterEnabledPublisher = BehaviorSubject.create();
    private final BluetoothManager manager;
    private final BluetoothAdapter adapter;

    LiveBluetoothSource(Context context, Settings settings, RealmSource realmSource, FakeSpeakerDevice fakeSpeakerDevice) {
        this.context = context;
        this.settings = settings;
        this.realmSource = realmSource;
        this.fakeSpeakerDevice = fakeSpeakerDevice;
        manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        Check.notNull(manager);
        adapter = manager.getAdapter();
        if (adapter == null) {
            Timber.e("BluetoothAdapter is null!");
            adapterEnabledPublisher.onNext(false);
            return;
        }
        adapterEnabledPublisher.onNext(adapter.isEnabled());

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

        HandlerThread handlerThread = new HandlerThread("BluetoothEventReceiver");
        handlerThread.start();

        Looper looper = handlerThread.getLooper();
        Handler handler = new Handler(looper);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Timber.d("Bluetooth event (intent=%s, extras=%s)", intent, intent.getExtras());
                String action = intent.getAction();
                if (action == null) {
                    Timber.e("Bluetooth event without action, how did we get this?");
                    return;
                }

                switch (action) {
                    case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                        updatePaired();
                        break;
                    case BluetoothAdapter.ACTION_STATE_CHANGED:
                        adapterEnabledPublisher.onNext(adapter.isEnabled());
                        updatePaired();
                        break;
                    case BluetoothDevice.ACTION_ACL_CONNECTED:
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                        SourceDevice.Event event = SourceDevice.Event.createEvent(intent);
                        if (event == null) {
                            Timber.e("Bad event intent: %s", intent);
                            return;
                        }
                        reloadConnectedDevices()
                                .doOnSubscribe(d -> Timber.i("Event based reloading until device is completely connected: %s", event))
                                .subscribeOn(Schedulers.io())
                                .map(deviceMap -> {
                                    if (event.getType() == SourceDevice.Event.Type.CONNECTED && !deviceMap.containsKey(event.getAddress())) {
                                        Timber.d("%s has connected, but is not shown as connected, retrying.", event);
                                        throw new MissingDeviceException(event);
                                    } else if (event.getType() == SourceDevice.Event.Type.DISCONNECTED && deviceMap.containsKey(event.getAddress())) {
                                        Timber.d("%s disconnected, but is still shown as connected, retrying.", event);
                                        throw new MissingDeviceException(event);
                                    }
                                    return deviceMap;
                                })
                                .retryWhen(new RetryWithDelay(60, 1000))
                                .subscribe((devices, throwable) -> {
                                    if (throwable != null) Timber.e(throwable, "Failed to get initial device info for %s.", event);
                                });
                        break;
                }
            }
        };
        context.registerReceiver(receiver, filter, null, handler);

        updatePaired();

        reloadConnectedDevices()
                .doOnSubscribe(disposable -> Timber.i("Initial load of connected devices."))
                .subscribeOn(Schedulers.io())
                .subscribe((devices, throwable) -> {
                    if (throwable != null) Timber.e(throwable, "Failed to get initial device infos.");
                });
    }

    @Override
    public Observable<Boolean> isEnabled() {
        return adapterEnabledPublisher;
    }

    private void updatePaired() {
        Timber.v("updatePaired()");

        if (ApiHelper.hasAndroid12() && ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Timber.w("BLUETOOTH_CONNECT permission is missing");
            return;
        }

        Single.defer(() -> Single.just(manager.getAdapter().getBondedDevices()))
                .map(bluetoothDevices -> {
                    Map<String, SourceDevice> devices = new HashMap<>();
                    for (BluetoothDevice realDevice : manager.getAdapter().getBondedDevices()) {

                        final ParcelUuid[] uuids = realDevice.getUuids();
                        if (hasUUID(uuids, 0x1400) && settings.isHealthDeviceExcluded()) {
                            Timber.w("Health devices are excluded, ignoring: %s", realDevice);
                            continue;
                        }

                        final SourceDeviceWrapper deviceWrapper = new SourceDeviceWrapper(realDevice);
                        devices.put(deviceWrapper.getAddress(), deviceWrapper);
                    }
                    devices.put(fakeSpeakerDevice.getAddress(), fakeSpeakerDevice);
                    Timber.d("Paired devices (%d): %s", devices.size(), devices);
                    return devices;
                })
                .subscribeOn(Schedulers.io())
                .subscribe(pairedPublisher::onNext, e -> Timber.e(e, "Updating paired devices failed."));
    }

    @Override
    public Observable<Map<String, SourceDevice>> pairedDevices() {
        Timber.v("pairedDevices()");
        return pairedPublisher;
    }

    private static boolean hasUUID(ParcelUuid[] uuids, int target) {
        if (uuids == null) return false;
        for (ParcelUuid uuid : uuids) {
            if (getServiceIdentifierFromParcelUuid(uuid) == target) return true;
        }
        return false;
    }

    private static int getServiceIdentifierFromParcelUuid(ParcelUuid parcelUuid) {
        UUID uuid = parcelUuid.getUuid();
        long value = (uuid.getMostSignificantBits() & 0x0000FFFF00000000L) >>> 32;
        return (int) value;
    }

    @Override
    public Single<Map<String, SourceDevice>> reloadConnectedDevices() {
        Timber.v("reloadConnectedDevices()");

        if (ApiHelper.hasAndroid12() && ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Timber.w("BLUETOOTH_CONNECT permission is missing");
            return Single.just(Collections.emptyMap());
        }

        return Single
                .defer(() -> {
                    final List<SingleSource<List<BluetoothDevice>>> profiles = new ArrayList<>();

                    if (!settings.isGATTExcluded()) {
                        profiles.add(LiveBluetoothSource.this.getDevicesForProfile(BluetoothProfile.GATT));
                    }
                    if (!settings.isGATTServerExcluded()) {
                        profiles.add(LiveBluetoothSource.this.getDevicesForProfile(BluetoothProfile.GATT_SERVER));
                    }

                    profiles.add(LiveBluetoothSource.this.getDevicesForProfile(BluetoothProfile.HEADSET));
                    profiles.add(LiveBluetoothSource.this.getDevicesForProfile(BluetoothProfile.A2DP));

                    if (!settings.isHealthDeviceExcluded()) {
                        profiles.add(LiveBluetoothSource.this.getDevicesForProfile(BluetoothProfile.HEALTH));
                    }

                    return Single.merge(profiles)
                            .toList()
                            .map(lists -> {
                                HashSet<BluetoothDevice> unique = new HashSet<>();
                                for (List<BluetoothDevice> ll : lists) unique.addAll(ll);
                                return unique;
                            })
                            .map(combined -> {
                                Timber.d("Connected COMBINED devices (%d): %s", combined.size(), combined);
                                Map<String, SourceDevice> connectedDevs = new HashMap<>();
                                for (BluetoothDevice d : combined) connectedDevs.put(d.getAddress(), new SourceDeviceWrapper(d));

                                final Set<String> managedAddrs = realmSource.getManagedAddresses().blockingGet();

                                boolean noManagedDeviceConnected = true;
                                for (String addr : connectedDevs.keySet()) {
                                    if (managedAddrs.contains(addr)) {
                                        noManagedDeviceConnected = false;
                                        break;
                                    }
                                }
                                if (noManagedDeviceConnected) {
                                    Timber.d("No (real) managed device is connected, connect fake speaker device %s", fakeSpeakerDevice);
                                    connectedDevs.put(fakeSpeakerDevice.getAddress(), fakeSpeakerDevice);
                                }

                                for (SourceDevice device : connectedDevs.values()) {
                                    if (!managedAddrs.contains(device.getAddress())) {
                                        Timber.d("%s is connected, but not managed by us.", device);
                                    }
                                }

                                return connectedDevs;
                            });
                })
                .doOnSuccess(connectedPublisher::onNext);
    }

    @Override
    public Observable<Map<String, SourceDevice>> connectedDevices() {
        return connectedPublisher;
    }

    private Single<List<BluetoothDevice>> getDevicesForProfile(int desiredProfile) {
        return Single
                .create((SingleOnSubscribe<List<BluetoothDevice>>) emitter -> {
                    long queryStart = System.currentTimeMillis();
                    BluetoothProfile.ServiceListener listener = new BluetoothProfile.ServiceListener() {
                        @SuppressWarnings("ConstantConditions")
                        public void onServiceConnected(int profile, BluetoothProfile proxy) {
                            final List<BluetoothDevice> connectedDevices = proxy.getConnectedDevices();
                            Timber.v("%dms to onServiceConnected(profile=%d, connected=%s)", (System.currentTimeMillis() - queryStart), profile, connectedDevices);
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
