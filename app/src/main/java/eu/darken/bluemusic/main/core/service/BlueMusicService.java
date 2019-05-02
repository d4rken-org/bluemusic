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
import android.util.SparseArray;

import com.bugsnag.android.Bugsnag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
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
import eu.darken.bluemusic.main.core.service.modules.EventModule;
import eu.darken.bluemusic.main.core.service.modules.VolumeModule;
import eu.darken.bluemusic.settings.core.Settings;
import eu.darken.bluemusic.util.ApiHelper;
import eu.darken.bluemusic.util.ui.RetryWithDelay;
import io.reactivex.Completable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
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
    @Inject Map<Class<? extends EventModule>, EventModule> eventModuleMap;
    @Inject Map<Class<? extends VolumeModule>, VolumeModule> volumeModuleMap;

    final Scheduler eventScheduler = Schedulers.from(Executors.newSingleThreadExecutor());
    final Scheduler volumeScheduler = Schedulers.from(Executors.newSingleThreadExecutor());
    private Disposable notificationSub;
    private Disposable isActiveSub;
    private final Map<String, CompositeDisposable> onGoingConnections = new LinkedHashMap<>();

    private BroadcastReceiver ringerPermission = new BroadcastReceiver() {
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

        notificationSub = deviceManager.devices()
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
        serviceHelper.start();
        if (intent == null) {
            Timber.w("Intent was null");
            serviceHelper.stop();
        } else if (intent.hasExtra(BluetoothEventReceiver.EXTRA_DEVICE_EVENT)) {
            final SourceDevice.Event event = intent.getParcelableExtra(BluetoothEventReceiver.EXTRA_DEVICE_EVENT);
            final RetryWithDelay retryWithDelay = new RetryWithDelay(300, 1000);
            bluetoothSource.reloadConnectedDevices()
                    .subscribeOn(eventScheduler)
                    .observeOn(eventScheduler)
                    .map(connectedDevices -> {
                        if (event.getType() == SourceDevice.Event.Type.CONNECTED && !connectedDevices.containsKey(event.getAddress())) {
                            Timber.w("%s not fully connected, retrying (#%d).", event.getDevice().getLabel(), retryWithDelay.getRetryCount());
                            serviceHelper.updateMessage(getString(R.string.description_waiting_for_devicex, event.getDevice().getLabel()) + " (#" + retryWithDelay.getRetryCount() + ")");
                            throw new MissingDeviceException(event);
                        }
                        return connectedDevices;
                    })
                    .retryWhen(retryWithDelay)
                    .flatMap(connectedDevices -> deviceManager.devices().firstOrError().map(managedDevices -> {
                        final ManagedDevice knownDevice = managedDevices.get(event.getAddress());
                        if (knownDevice == null) throw new UnmanagedDeviceException(event);
                        return knownDevice;
                    }))
                    .map(managedDevice -> new ManagedDevice.Action(managedDevice, event.getType()))
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
                        SparseArray<List<EventModule>> priorityArray = new SparseArray<>();
                        for (Map.Entry<Class<? extends EventModule>, EventModule> entry : eventModuleMap.entrySet()) {
                            final int priority = entry.getValue().getPriority();
                            List<EventModule> list = priorityArray.get(priority);
                            if (list == null) {
                                list = new ArrayList<>();
                                priorityArray.put(priority, list);
                            }
                            list.add(entry.getValue());
                        }

                        for (int i = 0; i < priorityArray.size(); i++) {
                            final List<EventModule> currentPriorityModules = priorityArray.get(priorityArray.keyAt(i));
                            Timber.d("%d event modules at priority %d", currentPriorityModules.size(), priorityArray.keyAt(i));

                            final CountDownLatch latch = new CountDownLatch(currentPriorityModules.size());
                            for (EventModule module : currentPriorityModules) {
                                Completable.fromRunnable(() -> {
                                    Timber.v("Event module %s HANDLE-START", module);
                                    module.handle(newDevice, event);
                                    Timber.v("Event module %s HANDLE-STOP", module);
                                })
                                        .subscribeOn(Schedulers.io())
                                        .doOnSubscribe(disp -> {
                                            Timber.d("Running event module %s", module);
                                            final CompositeDisposable comp = onGoingConnections.get(event.getAddress());
                                            if (comp != null) {
                                                Timber.v("Existing CompositeDisposable, adding %s to %s", module, comp);
                                                comp.add(disp);
                                            }
                                        })
                                        .doFinally(() -> {
                                            latch.countDown();
                                            Timber.d("Event module %s finished", module);
                                        })
                                        .subscribe(() -> { }, e -> {
                                            Timber.e(e, "Event module error");
                                            Bugsnag.notify(e);
                                        });
                            }
                            try {
                                latch.await();
                            } catch (InterruptedException e) {
                                Timber.w("Was waiting for %d event modules at priority %d but was INTERRUPTED", currentPriorityModules.size(), priorityArray.keyAt(i));
                                break;
                            }
                        }
                    })
                    .doOnSubscribe(disposable -> {
                        Timber.d("Subscribed %s", event);

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

                        // Do we need to keep the service running?
                        deviceManager.devices().firstOrError().subscribeOn(Schedulers.computation())
                                .map(deviceMap -> {
                                    boolean hasActiveDevices = false;
                                    for (ManagedDevice d : deviceMap.values()) {
                                        if (d.isActive() && !d.getAddress().equals(FakeSpeakerDevice.ADDR)) {
                                            hasActiveDevices = true;
                                            break;
                                        }
                                    }
                                    Timber.d("Active devices: %s", deviceMap);
                                    return hasActiveDevices;
                                })
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(hasActiveDevices -> {
                                    if (hasActiveDevices) {
                                        if (settings.isVolumeChangeListenerEnabled()) {
                                            Timber.d("We want to listen for volume changes!");
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
                        if (throwable != null && !(throwable instanceof UnmanagedDeviceException) && !(throwable instanceof MissingDeviceException)) {
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
        Single
                .create((SingleOnSubscribe<SparseArray<List<VolumeModule>>>) emitter -> {
                    SparseArray<List<VolumeModule>> priorityArray = new SparseArray<>();
                    for (Map.Entry<Class<? extends VolumeModule>, VolumeModule> entry : volumeModuleMap.entrySet()) {
                        final int priority = entry.getValue().getPriority();
                        List<VolumeModule> list = priorityArray.get(priority);
                        if (list == null) {
                            list = new ArrayList<>();
                            priorityArray.put(priority, list);
                        }
                        list.add(entry.getValue());
                    }
                    emitter.onSuccess(priorityArray);
                })
                .subscribeOn(volumeScheduler).observeOn(volumeScheduler)
                .subscribe(priorityArray -> {
                    for (int i = 0; i < priorityArray.size(); i++) {
                        final List<VolumeModule> currentPriorityModules = priorityArray.get(priorityArray.keyAt(i));
                        Timber.d("%d volume modules at priority %d", currentPriorityModules.size(), priorityArray.keyAt(i));

                        final CountDownLatch latch = new CountDownLatch(currentPriorityModules.size());
                        for (VolumeModule module : currentPriorityModules) {
                            Completable
                                    .fromRunnable(() -> {
                                        Timber.v("Volume module %s HANDLE-START", module);
                                        module.handle(id, volume);
                                        Timber.v("Volume module %s HANDLE-STOP", module);
                                    })
                                    .subscribeOn(Schedulers.io())
                                    .doFinally(() -> {
                                        latch.countDown();
                                        Timber.v("Volume module %s finished", module);
                                    })
                                    .subscribe(() -> { }, e -> {
                                        Timber.e(e, "Volume module error");
                                        Bugsnag.notify(e);
                                    });
                        }
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            Timber.w("Was waiting for %d volume modules at priority %d but was INTERRUPTED", currentPriorityModules.size(), priorityArray.keyAt(i));
                            break;
                        }
                    }

                }, e -> {
                    Timber.e(e, "Event module error");
                    Bugsnag.notify(e);
                });
    }
}