package eu.darken.bluetoothmanager.core;

import android.app.IntentService;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.media.AudioManager;

import java.util.Map;

import javax.inject.Inject;

import eu.darken.bluetoothmanager.App;
import eu.darken.bluetoothmanager.core.bluetooth.DaggerEventServiceComponent;
import eu.darken.bluetoothmanager.core.device.ManagedDevice;
import eu.darken.bluetoothmanager.core.device.ManagedDeviceRepo;
import timber.log.Timber;


public class EventService extends IntentService {
    public EventService() {
        super("BluetoothService");
    }

    @Inject ManagedDeviceRepo managedDeviceRepo;

    @Override
    protected void onHandleIntent(Intent intent) {
        DaggerEventServiceComponent.builder()
                .appComponent(App.Injector.INSTANCE.getAppComponent())
                .build().inject(this);
        Timber.d("onHandleIntent(%s)", intent);
        BluetoothDevice currentDevice = intent.getParcelableExtra(BluetoothEventReceiver.EXTRA_DEVICE);

        Map<String, ManagedDevice> wantedDevices = managedDeviceRepo.getMap();

        if (wantedDevices.isEmpty()) {
            Timber.w("Receiver was active but target devices are empty!");
            return;
        }

        if (!wantedDevices.containsKey(currentDevice.getAddress())) {
            Timber.d("Device %s is not among our targets.", currentDevice);
            return;
        } else Timber.d("Device %s is our target!", currentDevice.getAddress());

        String action = intent.getStringExtra(BluetoothEventReceiver.EXTRA_ACTION);
        if ("android.bluetooth.device.action.ACL_CONNECTED".equals(action)) {
            Timber.d("Handling device connected.");

            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            final AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_SHOW_UI);

        } else if ("android.bluetooth.device.action.ACL_DISCONNECTED".equals(action)) {
            Timber.d("Handling device disconnected.");
        }
        BluetoothEventReceiver.completeWakefulIntent(intent);
    }
}