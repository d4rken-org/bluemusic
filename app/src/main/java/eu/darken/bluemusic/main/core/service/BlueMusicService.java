package eu.darken.bluemusic.main.core.service;

import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.SparseArray;

import com.bugsnag.android.Bugsnag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import eu.darken.bluemusic.App;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.bluetooth.core.BluetoothEventReceiver;
import eu.darken.bluemusic.bluetooth.core.BluetoothSource;
import eu.darken.bluemusic.bluetooth.core.FakeSpeakerDevice;
import eu.darken.bluemusic.bluetooth.core.SourceDevice;
import eu.darken.bluemusic.main.core.audio.AudioStream;
import eu.darken.bluemusic.main.core.audio.StreamHelper;
import eu.darken.bluemusic.main.core.audio.VolumeObserver;
import eu.darken.bluemusic.main.core.database.DeviceManager;
import eu.darken.bluemusic.main.core.database.ManagedDevice;
import eu.darken.bluemusic.settings.core.Settings;
import eu.darken.bluemusic.util.ApiHelper;
import eu.darken.bluemusic.util.ui.RetryWithDelay;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;


public class BlueMusicService extends Service implements VolumeObserver.Callback {
    @Inject DeviceManager deviceManager;
    @Inject BluetoothSource bluetoothSource;
    @Inject StreamHelper streamHelper;
    @Inject VolumeObserver volumeObserver;
    @Inject Settings settings;
    @Inject ServiceHelper serviceHelper;
    @Inject Map<Class<? extends ActionModule>, ActionModule> actionModuleMap;

    final Scheduler scheduler = Schedulers.from(Executors.newSingleThreadExecutor());
    private volatile boolean adjusting = false;
    private Disposable notificationSub;
    private Disposable isActiveSub;
    private BroadcastReceiver ringerPermission = new BroadcastReceiver() {
        @SuppressWarnings("ConstantConditions")
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onReceive(Context context, Intent intent) {
            final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            Timber.d("isNotificationPolicyAccessGranted()=%b", notificationManager.isNotificationPolicyAccessGranted());
        }
    };
    private final Map<String, Disposable> onGoingConnections = new LinkedHashMap<>();

    @Override
    public void onCreate() {
        Timber.v("onCreate()");
        ((App) getApplication()).serviceInjector().inject(this);
        super.onCreate();

        for (AudioStream.Id id : AudioStream.Id.values()) {
            volumeObserver.addCallback(id, this);
        }
        getContentResolver().registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, volumeObserver);

        if (ApiHelper.hasMarshmallow()) {
            registerReceiver(ringerPermission, new IntentFilter(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED));
        }

        notificationSub = deviceManager.observe()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(stringManagedDeviceMap -> {
                    Collection<ManagedDevice> connected = new ArrayList<>();
                    for (ManagedDevice d : stringManagedDeviceMap.values()) {
                        if (d.isActive()) connected.add(d);
                    }
                    serviceHelper.updateActiveDevices(connected);
                });
        isActiveSub = bluetoothSource.isEnabled()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(isActive -> {
                    if (!isActive) serviceHelper.stop();
                });
    }

    @Override
    public void onDestroy() {
        Timber.v("onDestroy()");
        if (ApiHelper.hasMarshmallow()) {
            unregisterReceiver(ringerPermission);
        }
        notificationSub.dispose();
        isActiveSub.dispose();
        serviceHelper.stop();
        getContentResolver().unregisterContentObserver(volumeObserver);
        super.onDestroy();
    }

    class MBinder extends Binder {

    }

    private final MBinder binder = new MBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Timber.v("onUnbind(intent=%s)", intent);
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        Timber.v("onRebind(intent=%s)", intent);
        super.onRebind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Timber.v("onStartCommand-STARTED(intent=%s, flags=%d, startId=%d)", intent, flags, startId);
        if (intent == null) {
            Timber.w("Intent was null");
            serviceHelper.stop();
        } else if (intent.hasExtra(BluetoothEventReceiver.EXTRA_DEVICE_EVENT)) {
            serviceHelper.start();

            final SourceDevice.Event event = intent.getParcelableExtra(BluetoothEventReceiver.EXTRA_DEVICE_EVENT);

            if (event.getType() == SourceDevice.Event.Type.DISCONNECTED) {
                final Disposable disposable = onGoingConnections.remove(event.getAddress());
                if (disposable != null) {
                    Timber.d("%s disconnected, canceling on-going event %s", event.getAddress(), disposable);
                    disposable.dispose();
                }
            }

            // Do we need to keep the service running?
            bluetoothSource.getConnectedDevices()
                    .subscribeOn(scheduler)
                    .observeOn(scheduler)
                    .doOnSubscribe(disposable -> {
                        Timber.d("Handling %s", event);
                        adjusting = true;
                        if (event.getType() == SourceDevice.Event.Type.CONNECTED) {
                            onGoingConnections.put(event.getAddress(), disposable);
                        }
                    })
                    .map(connectedDevices -> {
                        if (event.getType() == SourceDevice.Event.Type.CONNECTED && !connectedDevices.containsKey(event.getAddress())) {
                            Timber.v("Connection not ready yet retrying.");
                            throw new PrematureConnectionException(event);
                        }
                        return event;
                    })
                    .retryWhen(new RetryWithDelay(5, 2000))
                    .flatMap(deviceEvent -> deviceManager.updateDevices().map(knownDevices -> {
                        final ManagedDevice knownDevice = knownDevices.get(deviceEvent.getAddress());
                        if (knownDevice == null) {
                            throw new UnmanagedDeviceException(deviceEvent);
                        }
                        return new ManagedDevice.Action(knownDevice, deviceEvent.getType());
                    }))
                    .doOnSuccess(action -> {
                        serviceHelper.updateMessage(getString(R.string.label_status_adjusting_volumes));

                        final ManagedDevice newDevice = action.getDevice();
                        SparseArray<List<ActionModule>> priorityArray = new SparseArray<>();
                        for (Map.Entry<Class<? extends ActionModule>, ActionModule> entry : actionModuleMap.entrySet()) {
                            final int priority = entry.getValue().getPriority();
                            List<ActionModule> list = priorityArray.get(priority);
                            if (list == null) {
                                list = new ArrayList<>();
                                priorityArray.put(priority, list);
                            }
                            list.add(entry.getValue());
                        }

                        for (int i = 0; i < priorityArray.size(); i++) {
                            final List<ActionModule> currentPriorityModules = priorityArray.get(priorityArray.keyAt(i));
                            Timber.d("%d modules at priority %d", currentPriorityModules.size(), priorityArray.keyAt(i));

                            final CountDownLatch latch = new CountDownLatch(currentPriorityModules.size());
                            for (ActionModule module : currentPriorityModules) {
                                new Thread(() -> {
                                    Timber.d("Running module %s", module);
                                    try {
                                        module.handle(newDevice, event);
                                    } catch (Exception e) {
                                        Timber.e(e, "Module error");
                                        Bugsnag.notify(e);
                                    } finally {
                                        latch.countDown();
                                    }
                                    Timber.d("Module %s finished", module);
                                }).start();
                            }
                            try {
                                latch.await();
                            } catch (InterruptedException e) { Timber.e(e); }
                        }
                    })
                    .doFinally(() -> {
                        final Disposable remove = onGoingConnections.remove(event.getAddress());
                        Timber.d("%s finished, removed: %s", event.getAddress(), remove);

                        adjusting = false;
                        Timber.d("Event handling finished.");
                        // Do we need to keep the service running?
                        deviceManager.observe().firstOrError().subscribeOn(Schedulers.computation())
                                .subscribe(deviceMap -> {
                                    boolean hasActiveDevices = false;
                                    for (ManagedDevice d : deviceMap.values()) {
                                        if (d.isActive() && !d.getAddress().equals(FakeSpeakerDevice.ADDR)) {
                                            hasActiveDevices = true;
                                            break;
                                        }
                                    }
                                    if (hasActiveDevices) {
                                        if (settings.isVolumeChangeListenerEnabled()) {
                                            serviceHelper.updateMessage(getString(R.string.label_status_listening_for_changes));
                                        } else {
                                            Timber.d("We don't want to listen to anymore volume changes, stopping service.");
                                            serviceHelper.stop();
                                        }
                                    } else {
                                        Timber.d("No more active devices, stopping service.");
                                        serviceHelper.stop();
                                    }
                                });
                    })
                    .subscribe((action, throwable) -> {
                        if (throwable != null && !(throwable instanceof UnmanagedDeviceException) && !(throwable instanceof PrematureConnectionException)) {
                            Timber.e(throwable, "Device error");
                            Bugsnag.notify(throwable);
                        }

                    });
        } else if (ServiceHelper.STOP_ACTION.equals(intent.getAction())) {
            serviceHelper.stop();
        } else {
            serviceHelper.stop();
        }

        Timber.v("onStartCommand-END(intent=%s, flags=%d, startId=%d)", intent, flags, startId);
        return START_NOT_STICKY;
    }

    @Override
    public void onVolumeChanged(AudioStream.Id id, int volume) {
        if (!settings.isVolumeChangeListenerEnabled()) {
            Timber.v("Volume listener is disabled.");
            return;
        }
        if (adjusting || streamHelper.wasUs(id, volume)) {
            Timber.v("Volume change was triggered by us, ignoring it.");
            return;
        }
        float percentage = streamHelper.getVolumePercentage(id);
        deviceManager.updateDevices()
                .map(deviceMap -> {
                    Collection<ManagedDevice> active = new HashSet<>();
                    for (ManagedDevice d : deviceMap.values()) {
                        if (d.isActive()) active.add(d);
                    }
                    return active;
                })
                .filter(managedDevices -> !managedDevices.isEmpty())
                .toFlowable()
                .flatMapIterable(managedDevices -> managedDevices)
                .map(device -> {
                    AudioStream.Type streamType = device.getStreamType(id);
                    if (device.getVolume(streamType) != null) {
                        device.setVolume(streamType, percentage);
                    }
                    return device;
                })
                .toList()
                .subscribe(actives -> deviceManager.save(actives).subscribeOn(Schedulers.computation()).subscribe());
    }
}