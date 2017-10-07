package eu.darken.bluemusic.core.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.bugsnag.android.Bugsnag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import eu.darken.bluemusic.App;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.core.bluetooth.BluetoothEventReceiver;
import eu.darken.bluemusic.core.bluetooth.BluetoothSource;
import eu.darken.bluemusic.core.bluetooth.SourceDevice;
import eu.darken.bluemusic.core.database.DeviceManager;
import eu.darken.bluemusic.core.database.ManagedDevice;
import eu.darken.bluemusic.core.settings.Settings;
import io.reactivex.Scheduler;
import io.reactivex.SingleSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
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
    @Inject List<ActionModule> actionModules;
    final Scheduler scheduler = Schedulers.from(Executors.newSingleThreadExecutor());
    private volatile boolean adjusting = false;
    private Disposable notificationSub;
    private Disposable isActiveSub;

    @Override
    public void onCreate() {
        Timber.v("onCreate()");
        ((App) getApplication()).serviceInjector().inject(this);
        super.onCreate();

        volumeObserver.addCallback(streamHelper.getMusicId(), this);
        volumeObserver.addCallback(streamHelper.getCallId(), this);
        getContentResolver().registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, volumeObserver);

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
                    if (!isActive) stopSelf();
                });
    }

    @Override
    public void onDestroy() {
        Timber.v("onDestroy()");
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
        Timber.v("onStartCommand(intent=%s, flags=%d, startId=%d)", intent, flags, startId);
        if (intent == null) {
            Timber.w("Intent was null");
            stopSelf();
        } else if (intent.hasExtra(BluetoothEventReceiver.EXTRA_DEVICE_EVENT)) {
            serviceHelper.start();

            SourceDevice.Event event = intent.getParcelableExtra(BluetoothEventReceiver.EXTRA_DEVICE_EVENT);

            bluetoothSource.getConnectedDevices()
                    .delaySubscription(1000, TimeUnit.MILLISECONDS)
                    .subscribeOn(scheduler)
                    .map(connectedDevices -> {
                        if (event.getType() == SourceDevice.Event.Type.CONNECTED && !connectedDevices.containsKey(event.getAddress())) {
                            Thread.sleep(500);
                            throw new PrematureConnectionException(event);
                        }
                        return event;
                    })
                    .retry(20)
                    .flatMap(new Function<SourceDevice.Event, SingleSource<ManagedDevice.Action>>() {
                        @Override
                        public SingleSource<ManagedDevice.Action> apply(SourceDevice.Event deviceEvent) throws Exception {
                            return deviceManager.updateDevices()
                                    .map(knownDevices -> {
                                        final ManagedDevice knownDevice = knownDevices.get(deviceEvent.getAddress());
                                        if (knownDevice == null) {
                                            throw new UnmanagedDeviceException(deviceEvent);
                                        }
                                        return new ManagedDevice.Action(knownDevice, deviceEvent.getType());
                                    });
                        }
                    })
                    .doOnSubscribe(disposable -> {
                        Timber.d("Handling %s", event);
                        adjusting = true;
                    })
                    .doFinally(() -> {
                        adjusting = false;
                        Timber.d("Adjustment finished.");

                        serviceHelper.updateMessage(getString(R.string.label_status_idle));

                        if (!settings.isVolumeChangeListenerEnabled()) {
                            Timber.d("We don't want to listen to volume changes, stopping service.");
                            stopSelf();
                        } else {
                            serviceHelper.updateMessage(getString(R.string.label_status_listening_for_changes));
                        }
                    })
                    .subscribe((action, throwable) -> {
                        if (throwable != null) {
                            if (!(throwable instanceof UnmanagedDeviceException)
                                    && !(throwable instanceof PrematureConnectionException)) {
                                Bugsnag.notify(throwable);
                            }
                            return;
                        }

                        final ManagedDevice device = action.getDevice();
                        serviceHelper.updateMessage(getString(R.string.label_status_adjusting_volumes));
                        final CountDownLatch latch = new CountDownLatch(actionModules.size());
                        for (ActionModule module : actionModules) {
                            new Thread(() -> {
                                Timber.d("Running module %s", module);
                                try {
                                    module.handle(device, event);
                                } catch (Exception e) {
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

                        if (event.getType() == SourceDevice.Event.Type.DISCONNECTED) {
                            handleDisconnect();
                        }
                    });
        } else if (ServiceHelper.STOP_ACTION.equals(intent.getAction())) {
            stopSelf();
        } else {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void handleDisconnect() {
        deviceManager.observe()
                .firstOrError()
                .subscribeOn(Schedulers.computation())
                .subscribe(deviceMap -> {
                    boolean stop = true;
                    for (ManagedDevice d : deviceMap.values()) {
                        if (d.isActive()) {
                            stop = false;
                            break;
                        }
                    }
                    if (stop) {
                        Timber.d("No more active devices, stopping service.");
                        stopSelf();
                    }
                });
    }

    @Override
    public void onVolumeChanged(int streamId, int volume) {
        if (!settings.isVolumeChangeListenerEnabled()) {
            Timber.v("Volume listener is disabled.");
            return;
        }
        if (adjusting || streamHelper.wasUs(streamId, volume)) {
            Timber.v("Volume change was triggered by us, ignoring it.");
            return;
        }
        float percentage = streamHelper.getVolumePercentage(streamId);
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
                    if (streamId == streamHelper.getCallId()) device.setCallVolume(percentage);
                    else device.setMusicVolume(percentage);
                    return device;
                })
                .toList()
                .subscribe(actives -> deviceManager.save(actives).subscribeOn(Schedulers.computation()).subscribe());
    }
}