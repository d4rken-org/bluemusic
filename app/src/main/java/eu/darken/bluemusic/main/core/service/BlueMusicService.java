package eu.darken.bluemusic.main.core.service;

import android.annotation.SuppressLint;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
import io.reactivex.Completable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
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
    private final Map<String, CompositeDisposable> onGoingConnections = new LinkedHashMap<>();

    private BroadcastReceiver ringerPermission = new BroadcastReceiver() {
        @SuppressWarnings("ConstantConditions")
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onReceive(Context context, Intent intent) {
            final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            Timber.d("isNotificationPolicyAccessGranted()=%b", notificationManager.isNotificationPolicyAccessGranted());
        }
    };

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
        getContentResolver().unregisterContentObserver(volumeObserver);
        if (ApiHelper.hasMarshmallow()) {
            unregisterReceiver(ringerPermission);
        }
        notificationSub.dispose();
        isActiveSub.dispose();
        serviceHelper.stop();
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

    @SuppressLint("ThrowableNotAtBeginning")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Timber.v("onStartCommand-STARTED(intent=%s, flags=%d, startId=%d)", intent, flags, startId);
        if (intent == null) {
            Timber.w("Intent was null");
            serviceHelper.stop();
        } else if (intent.hasExtra(BluetoothEventReceiver.EXTRA_DEVICE_EVENT)) {
            serviceHelper.start();

            final SourceDevice.Event event = intent.getParcelableExtra(BluetoothEventReceiver.EXTRA_DEVICE_EVENT);

            bluetoothSource.getConnectedDevices()
                    .subscribeOn(scheduler)
                    .observeOn(scheduler)
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
                    .flatMap((Function<ManagedDevice.Action, SingleSource<ManagedDevice.Action>>) action -> {
                        if (action.getType() == SourceDevice.Event.Type.CONNECTED) {
                            serviceHelper.updateMessage(getString(R.string.label_reaction_delay));
                            Long reactionDelay = action.getDevice().getActionDelay();
                            if (reactionDelay == null) reactionDelay = Settings.DEFAULT_REACTION_DELAY;
                            Timber.d("Delaying reaction to %s by %d ms.", action, reactionDelay);
                            return Single.timer(reactionDelay, TimeUnit.MILLISECONDS).map(timer -> action);
                        } else {
                            return Single.just(action);
                        }
                    })
                    .doOnSuccess(action -> {
                        Timber.d("Acting on %s", action);
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
                                Completable.fromRunnable(() -> module.handle(newDevice, event))
                                        .subscribeOn(Schedulers.io())
                                        .doOnSubscribe(disp -> {
                                            Timber.d("Running module %s", module);
                                            final CompositeDisposable comp = onGoingConnections.get(event.getAddress());
                                            if (comp != null) {
                                                Timber.v("Existing CompositeDisposable, adding %s to %s", module, comp);
                                                onGoingConnections.get(event.getAddress()).add(disp);
                                            }
                                        })
                                        .doFinally(() -> {
                                            latch.countDown();
                                            Timber.d("Module %s finished", module);
                                        })
                                        .subscribe(() -> { }, e -> {
                                            Timber.e(e, "Module error");
                                            Bugsnag.notify(e);
                                        });
                            }
                            try {
                                latch.await();
                            } catch (InterruptedException e) {
                                Timber.w("Was waitinf for %d modules at priority %d but was INTERRUPTED", currentPriorityModules.size(), priorityArray.keyAt(i));
                                break;
                            }
                        }
                    })
                    .doOnSubscribe(disposable -> {
                        Timber.d("Subscribed %s", event);
                        adjusting = true;

                        if (event.getType() == SourceDevice.Event.Type.CONNECTED) {
                            CompositeDisposable compositeDisposable = new CompositeDisposable();
                            compositeDisposable.add(disposable);
                            onGoingConnections.put(event.getAddress(), compositeDisposable);
                        } else if (event.getType() == SourceDevice.Event.Type.DISCONNECTED) {
                            final CompositeDisposable eventActions = onGoingConnections.remove(event.getAddress());
                            if (eventActions != null) {
                                Timber.d("%s disconnected, canceling on-going event (%d actions)", event.getAddress(), eventActions.size());
                                eventActions.dispose();
                            }
                        }
                    })
                    .doOnDispose(() -> Timber.d("Disposed %s", event))
                    .doFinally(() -> {
                        final CompositeDisposable remove = onGoingConnections.remove(event.getAddress());
                        Timber.d("%s finished, removed: %s", event.getAddress(), remove);
                        adjusting = false;

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
                                        Timber.d("No more active devices, stopping service (%s).", BlueMusicService.this);
                                        serviceHelper.stop();
                                    }
                                });
                    })
                    .subscribe((action, throwable) -> {
                        Timber.d("action=%s, throwable=%s", action, throwable);
                        if (throwable != null && !(throwable instanceof UnmanagedDeviceException) && !(throwable instanceof PrematureConnectionException)) {
                            Timber.e(throwable, "Device error");
                            Bugsnag.notify(throwable);
                        }

                    });

        } else if (ServiceHelper.STOP_ACTION.equals(intent.getAction())) {
            Timber.d("Stopping service, currently %d on-going events, killing them.", onGoingConnections.size());
            final HashMap<String, CompositeDisposable> tmp = new HashMap<>(onGoingConnections);
            onGoingConnections.clear();
            for (Map.Entry<String, CompositeDisposable> entry : tmp.entrySet()) {
                entry.getValue().dispose();
            }

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
                .filter(device -> device.getStreamType(id) != null)
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