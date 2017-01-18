package eu.darken.bluemusic.core;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;

import java.util.Map;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import eu.darken.bluemusic.App;
import eu.darken.bluemusic.core.bluetooth.BluetoothEventReceiver;
import eu.darken.bluemusic.core.bluetooth.SourceDevice;
import eu.darken.bluemusic.core.database.DeviceManager;
import eu.darken.bluemusic.core.database.ManagedDevice;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Scheduler;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

import static android.R.attr.action;


public class BlueMusicService extends Service implements VolumeObserver.Callback {
    @Inject DeviceManager deviceManager;

    final Scheduler scheduler = Schedulers.from(Executors.newSingleThreadExecutor());
    private VolumeObserver contentObserver;
    private AudioManager audioManager;
    private final long delay = 5000;
    private volatile boolean inProgress = false;

    @Override
    public void onCreate() {
        Timber.v("onCreate()");
        App.Injector.INSTANCE.getAppComponent().blueMusicServiceComponent().inject(this);
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        contentObserver = new VolumeObserver(new Handler(), audioManager);
        contentObserver.addCallback(AudioManager.STREAM_MUSIC, this);
        getContentResolver().registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, contentObserver);
    }

    @Override
    public void onDestroy() {
        getContentResolver().unregisterContentObserver(contentObserver);
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
        if (_intent != null) {
            Observable.fromCallable(() -> _intent)
                    .subscribeOn(scheduler)
                    .map(intent -> {
                        SourceDevice.Action deviceAction = intent.getParcelableExtra(BluetoothEventReceiver.EXTRA_DEVICE_ACTION);
                        if (deviceAction == null) {
                            Timber.e("Unknown intent (%s)", intent);
                            return null;
                        }
                        return deviceAction;
                    })
                    .flatMap(new Function<SourceDevice.Action, ObservableSource<ManagedDevice.Action>>() {
                        @Override
                        public ObservableSource<ManagedDevice.Action> apply(SourceDevice.Action deviceAction) throws Exception {
                            return deviceManager.getDevices().map(deviceMap -> new ManagedDevice.Action(deviceMap.get(deviceAction.getAddress()), deviceAction.getType()));
                        }
                    })
                    .subscribe(this::handleDevice);
        } else Timber.w("Intent was null");
        return super.onStartCommand(_intent, flags, startId);
    }

    private void handleDevice(ManagedDevice.Action event) {
        Timber.d("Handling %s", event);
        ManagedDevice device = event.getDevice();
        if (event.getType() == SourceDevice.Action.Type.CONNECTED) {
            inProgress = true;
            handleConnect(device);
            inProgress = false;
        } else if (event.getType() == SourceDevice.Action.Type.DISCONNECTED) {
            handleDisconnect(device);
        } else {
            Timber.w("Unknown intent action: %s", action);
        }
    }

    private void handleConnect(ManagedDevice connectedDevice) {
        float percentage = connectedDevice.getVolumePercentage();
        if (percentage != -1) {
            try {
                Timber.d("Waiting %d ms for system to screwup the volume.", delay);
                Thread.sleep(delay);
            } catch (InterruptedException e) { Timber.e(e, null); }

            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int targetVolume = Math.round(percentage * maxVolume);
            int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (currentVolume != targetVolume) {
                Timber.i(
                        "Adjusting volume (target=%d, percentage=%.2f, current=%d, max=%d) due to device %s",
                        targetVolume, percentage, currentVolume, maxVolume, connectedDevice
                );
                for (int volumeStep = currentVolume; volumeStep < targetVolume + 1; volumeStep++) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeStep, AudioManager.FLAG_SHOW_UI);
                    Timber.v("Setting volume to: %d", volumeStep);
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) { Timber.e(e, null); }
                }
            } else {
                Timber.d("Target volume of %d already set for device %s", targetVolume, connectedDevice);
            }
        } else {
            Timber.d("Device %s has no specified target volume yet, skipping adjustments.", connectedDevice);
        }
    }

    private void handleDisconnect(ManagedDevice disconnectedDevice) {
        final Map<String, ManagedDevice> deviceMap = deviceManager.loadManagedDevices().blockingFirst();
        boolean stop = true;
        for (ManagedDevice device : deviceMap.values()) {
            if (device.isActive()) {
                stop = false;
                break;
            }
        }
        if (stop) {
            Timber.i("No more active devices, stopping service.");
            stopSelf();
        }
    }

    @Override
    public void onVolumeChanged(int streamId, int volume) {
        final int maxVolume = audioManager.getStreamMaxVolume(streamId);
        float percentage = (float) volume / maxVolume;
        if (inProgress) {
            Timber.v("Device connection in progress. Ignoring volume changes.");
            return;
        }
        deviceManager.updateVolume(streamId, percentage);
    }
}