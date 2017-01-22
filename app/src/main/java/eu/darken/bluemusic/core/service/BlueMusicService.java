package eu.darken.bluemusic.core.service;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.os.IBinder;
import android.support.annotation.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import eu.darken.bluemusic.App;
import eu.darken.bluemusic.core.Settings;
import eu.darken.bluemusic.core.bluetooth.BluetoothEventReceiver;
import eu.darken.bluemusic.core.bluetooth.BluetoothSource;
import eu.darken.bluemusic.core.bluetooth.SourceDevice;
import eu.darken.bluemusic.core.database.DeviceManager;
import eu.darken.bluemusic.core.database.ManagedDevice;
import io.reactivex.Scheduler;
import io.reactivex.SingleObserver;
import io.reactivex.SingleSource;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

import static android.R.attr.action;


public class BlueMusicService extends Service implements VolumeObserver.Callback {
    @Inject DeviceManager deviceManager;
    @Inject BluetoothSource bluetoothSource;
    @Inject StreamHelper streamHelper;
    @Inject VolumeObserver volumeObserver;
    @Inject Settings settings;
    final Scheduler scheduler = Schedulers.from(Executors.newSingleThreadExecutor());
    private volatile boolean adjusting = false;

    @Override
    public void onCreate() {
        Timber.v("onCreate()");
        App.Injector.INSTANCE.getAppComponent().blueMusicServiceComponent().inject(this);
        super.onCreate();
        volumeObserver.addCallback(streamHelper.getMusicId(), this);
        volumeObserver.addCallback(streamHelper.getVoiceId(), this);
        getContentResolver().registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, volumeObserver);
    }

    @Override
    public void onDestroy() {
        getContentResolver().unregisterContentObserver(volumeObserver);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent _intent, int flags, int startId) {
        Timber.v("onStartCommand(%s)", _intent);
        if (_intent == null) {
            Timber.w("Intent was null");
            return START_STICKY;
        }
        SourceDevice.Event event = _intent.getParcelableExtra(BluetoothEventReceiver.EXTRA_DEVICE_EVENT);
        if (event != null) {
            bluetoothSource.getConnectedDevices()
                    .delaySubscription(1000, TimeUnit.MILLISECONDS)
                    .subscribeOn(scheduler)
                    .map(deviceMap -> {
                        if (event.getType() == SourceDevice.Event.Type.CONNECTED && !deviceMap.containsKey(event.getAddress())) {
                            Thread.sleep(500);
                            throw new Exception("Device not yet fully connected.");
                        }
                        return event;
                    })
                    .retry(20)
                    .flatMap(new Function<SourceDevice.Event, SingleSource<ManagedDevice.Action>>() {
                        @Override
                        public SingleSource<ManagedDevice.Action> apply(SourceDevice.Event deviceEvent) throws Exception {
                            return deviceManager.load(true).map(deviceMap -> new ManagedDevice.Action(deviceMap.get(deviceEvent.getAddress()), deviceEvent.getType()));
                        }
                    })
                    .subscribe(new SingleObserver<ManagedDevice.Action>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                            adjusting = true;
                            Timber.v("Incoming adjustment.");
                        }

                        @Override
                        public void onSuccess(ManagedDevice.Action value) {
                            Timber.d("Handling %s", event);
                            ManagedDevice device = value.getDevice();
                            if (event.getType() == SourceDevice.Event.Type.CONNECTED) {
                                handleConnect(device);
                            } else if (event.getType() == SourceDevice.Event.Type.DISCONNECTED) {
                                handleDisconnect();
                            } else {
                                Timber.w("Unknown intent action: %s", action);
                            }
                            adjusting = false;
                            Timber.v("Adjustment finished.");
                        }

                        @Override
                        public void onError(Throwable e) {
                            adjusting = false;
                            Timber.e(e, null);
                        }
                    });
        }

        return START_STICKY;
    }

    private void handleConnect(ManagedDevice dev) {
        float percentageMusic = dev.getMusicVolume();
        float percentageVoice = dev.getMusicVolume();
        if (percentageMusic != -1 || percentageVoice != -1) {
            try {
                long delay = settings.getFudgeDelay();
                Timber.d("Waiting %d ms for system to screwup the volume.", delay);
                Thread.sleep(delay);
            } catch (InterruptedException e) { Timber.e(e, null); }
            if (percentageMusic != -1) handleStream(streamHelper.getMusicId(), dev.getRealMusicVolume(), dev.getMaxMusicVolume());
            if (percentageVoice != -1) handleStream(streamHelper.getVoiceId(), dev.getRealVoiceVolume(), dev.getMaxVoiceVolume());
        } else {
            Timber.d("Device %s has no specified target volume yet, skipping adjustments.", dev);
        }
    }

    private void handleStream(int streamId, int target, int max) {
        int currentVolume = streamHelper.getVolume(streamId);
        if (currentVolume != target) {
            Timber.i(
                    "Adjusting volume (stream=%d, target=%d, current=%d, max=%d).",
                    streamId, target, currentVolume, max
            );
            if (currentVolume < target) {
                for (int volumeStep = currentVolume; volumeStep <= target; volumeStep++) {
                    streamHelper.setStreamVolume(streamId, volumeStep, AudioManager.FLAG_SHOW_UI);
                    try { Thread.sleep(250); } catch (InterruptedException e) { Timber.e(e, null); }
                }
            } else {
                for (int volumeStep = currentVolume; volumeStep >= target; volumeStep--) {
                    streamHelper.setStreamVolume(streamId, volumeStep, AudioManager.FLAG_SHOW_UI);
                    try { Thread.sleep(250); } catch (InterruptedException e) { Timber.e(e, null); }
                }
            }
        } else Timber.d("Target volume of %d already set.", target);

    }

    private void handleDisconnect() {
        deviceManager.load(true)
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
                        Timber.i("No more active devices, stopping service.");
                        stopSelf();
                    }
                });
    }

    @Override
    public void onVolumeChanged(int streamId, int volume) {
        if (adjusting || streamHelper.wasUs(streamId, volume)) {
            Timber.v("Volume change was triggered by us, ignoring it.");
            return;
        }
        float percentage = streamHelper.getVolumePercentage(streamId);
        deviceManager.load(false)
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
                    if (streamId == streamHelper.getVoiceId()) device.setVoiceVolume(percentage);
                    else device.setMusicVolume(percentage);
                    return device;
                })
                .toList()
                .subscribe(actives -> {
                    deviceManager.update(actives)
                            .subscribeOn(Schedulers.computation())
                            .subscribe(managedDevices -> {
                                deviceManager.notifyChanged(managedDevices);
                            });
                });
    }
}