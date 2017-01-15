package eu.darken.bluemusic.core;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import eu.darken.bluemusic.App;
import eu.darken.bluemusic.core.bluetooth.BluetoothEventReceiver;
import eu.darken.bluemusic.core.bluetooth.BluetoothSource;
import eu.darken.bluemusic.core.bluetooth.Device;
import eu.darken.bluemusic.core.database.ManagedDevice;
import eu.darken.bluemusic.core.database.ManagedDeviceRepo;
import eu.darken.bluemusic.util.Tools;
import timber.log.Timber;


public class BlueMusicService extends Service implements VolumeObserver.Callback {
    @Inject ManagedDeviceRepo managedDeviceRepo;
    @Inject BluetoothSource bluetoothSource;
    final Map<String, ManagedDevice> activeDevices = new HashMap<>();
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    private VolumeObserver contentObserver;
    private AudioManager audioManager;
    private final long delay = 5000;
    private volatile boolean inProgress = false;

    @Override
    public void onCreate() {
        Timber.v("onCreate()");
        DaggerBlueMusicServiceComponent.builder()
                .appComponent(App.Injector.INSTANCE.getAppComponent())
                .build().inject(this);
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
    public int onStartCommand(Intent intent, int flags, int startId) {
        Timber.v("onStartCommand(%s)", intent);
        if (intent != null) {
            executor.submit(() -> handleIntent(intent));
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void handleIntent(Intent intent) {
        String address = intent.getStringExtra(BluetoothEventReceiver.EXTRA_DEVICE_ADDRESS);
        if (address == null) {
            Timber.e("Unknown intent (%s)", intent);
            return;
        }

        ManagedDevice device = managedDeviceRepo.getDevice(address);
        if (device == null) {
            Timber.d("Unmanaged device, creating new managed device.");
            Device newDev = Tools.toMap(bluetoothSource.getPairedDevices()).get(address);
            if (newDev == null) {
                Timber.e("Can't find device: %s", address);
                return;
            }
            device = managedDeviceRepo.manage(newDev);
            Timber.i("New managed device: %s", device);
        }

        Timber.d("Handling %s", device);

        String action = intent.getStringExtra(BluetoothEventReceiver.EXTRA_ACTION);
        if ("android.bluetooth.device.action.ACL_CONNECTED".equals(action)) {
            inProgress = true;
            float percentage = device.getVolumePercentage();
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
                            targetVolume, percentage, currentVolume, maxVolume, device
                    );
                    for (int volumeStep = currentVolume; volumeStep < targetVolume + 1; volumeStep++) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeStep, AudioManager.FLAG_SHOW_UI);
                        Timber.v("Setting volume to: %d", volumeStep);
                        try {
                            Thread.sleep(250);
                        } catch (InterruptedException e) { Timber.e(e, null); }
                    }
                } else {
                    Timber.d("Target volume of %d already set for device %s", targetVolume, device);
                }
            } else {
                Timber.d("Device %s has no specified target volume yet, skipping adjustments.", device);
            }
            if (!activeDevices.containsKey(device.getAddress())) {
                Timber.d("Now active: %s", device);
                activeDevices.put(device.getAddress(), device);
            }
            inProgress = false;
        } else if ("android.bluetooth.device.action.ACL_DISCONNECTED".equals(action)) {
            Timber.d("No longer active: %s", device);
            activeDevices.remove(device.getAddress());
            if (activeDevices.isEmpty()) {
                Timber.d("No more active devices, stopping service.");
                stopSelf();
            }
        } else {
            Timber.w("Unknown intent action: %s", action);
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

        Timber.d("New volume percentage (%.2f), updating active devices.", percentage);
        for (ManagedDevice managedDevice : activeDevices.values()) {
            managedDevice.setVolumePercentage(percentage);
        }
        managedDeviceRepo.update(activeDevices.values());
    }
}