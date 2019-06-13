package eu.darken.bluemusic.bluetooth.core;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.bugsnag.android.Bugsnag;
import com.bugsnag.android.Severity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import dagger.android.HasBroadcastReceiverInjector;
import eu.darken.bluemusic.main.core.audio.AudioStream;
import eu.darken.bluemusic.main.core.audio.StreamHelper;
import eu.darken.bluemusic.main.core.database.DeviceManager;
import eu.darken.bluemusic.main.core.database.ManagedDevice;
import eu.darken.bluemusic.main.core.database.RealmSource;
import eu.darken.bluemusic.main.core.service.ServiceHelper;
import eu.darken.bluemusic.settings.core.Settings;
import eu.darken.mvpbakery.injection.broadcastreceiver.HasManualBroadcastReceiverInjector;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;


public class BluetoothEventReceiver extends BroadcastReceiver {
    public static final String EXTRA_DEVICE_EVENT = "eu.darken.bluemusic.core.bluetooth.event";

    @Inject Settings settings;
    @Inject RealmSource realmSource;
    @Inject StreamHelper streamHelper;
    @Inject FakeSpeakerDevice fakeSpeakerDevice;
    @Inject DeviceManager deviceManager;

    @Override
    public void onReceive(Context context, Intent intent) {
        Timber.v("onReceive(%s, %s)", context, intent);
        // https://stackoverflow.com/questions/46784685
        // https://stackoverflow.com/questions/41061272
        // https://issuetracker.google.com/issues/37137009
        if (!(context.getApplicationContext() instanceof HasManualBroadcastReceiverInjector)) {
            Exception ex = new RuntimeException(String.format(
                    "%s does not implement %s",
                    context.getApplicationContext().getClass().getCanonicalName(),
                    HasBroadcastReceiverInjector.class.getCanonicalName()));
            Bugsnag.notify(ex, Severity.WARNING);
            return;
        }
        ((HasManualBroadcastReceiverInjector) context.getApplicationContext()).broadcastReceiverInjector().inject(this);

        if (!settings.isEnabled()) {
            Timber.i("We are disabled.");
            return;
        }

        final SourceDevice.Event deviceEvent = SourceDevice.Event.createEvent(intent);
        if (deviceEvent == null) {
            Timber.e("Couldn't create device event for %s", intent);
            return;
        }

        final PendingResult goAsync = goAsync();

        deviceManager.devices().firstOrError()
                .subscribeOn(Schedulers.io())
                .timeout(8, TimeUnit.SECONDS)
                .doOnSuccess(devices -> Timber.d("Managed devices: %s", devices))
                .filter(devices -> {
                    final ManagedDevice managedDevice = devices.get(deviceEvent.getAddress());
                    if (managedDevice == null) Timber.d("Event %s belongs to an un-managed device", deviceEvent);
                    else Timber.d("Event %s concerns device %s", deviceEvent, managedDevice);
                    return managedDevice != null;
                })
                .doOnSuccess(managedDevices -> {
                    if (!settings.isSpeakerAutoSaveEnabled()) {
                        Timber.d("Autosave for the device speaker is not enabled.");
                        return;
                    }
                    if (deviceEvent.getAddress().equals(FakeSpeakerDevice.ADDR)) return;
                    if (deviceEvent.getType() != SourceDevice.Event.Type.CONNECTED) return;

                    ManagedDevice fakeSpeaker = managedDevices.get(FakeSpeakerDevice.ADDR);
                    if (fakeSpeaker == null) {
                        Timber.i("FakeSpeaker device not yet managed, adding.");
                        fakeSpeaker = deviceManager.addNewDevice(fakeSpeakerDevice).blockingGet();
                    }

                    // Don't overwrite ourselves
                    Map<String, ManagedDevice> activeDevices = new HashMap<>();
                    for (ManagedDevice device : managedDevices.values()) {
                        if (device.isActive()) activeDevices.put(device.getAddress(), device);
                    }
                    if (activeDevices.size() >= 2 && !activeDevices.containsKey(FakeSpeakerDevice.ADDR)) {
                        Timber.d("Not saving volume, at least 2 Bluetooth devices already connected");
                        return;
                    } else if (activeDevices.size() == 1 && !activeDevices.containsKey(deviceEvent.getAddress()) && !activeDevices.containsKey(FakeSpeakerDevice.ADDR)) {
                        Timber.d("Not saving volume, one Bluetooth device already that isn't the speaker or this.");
                        return;
                    }

                    final ManagedDevice eventDevice = managedDevices.get(deviceEvent.getAddress());
                    final Map<AudioStream.Type, Float> affectedValues = new HashMap<>();
                    for (AudioStream.Type type : AudioStream.Type.values()) {
                        final Float eventDevTypeVol = eventDevice.getVolume(type);
                        if (eventDevTypeVol == null) continue;

                        final float currentVol = streamHelper.getVolumePercentage(fakeSpeaker.getStreamId(type));
                        if (currentVol == eventDevTypeVol) continue;
                        affectedValues.put(type, currentVol);
                    }

                    Timber.d("The connecting device will affect: %s", affectedValues);
                    if (!affectedValues.isEmpty()) return;

                    for (Map.Entry<AudioStream.Type, Float> entry : affectedValues.entrySet()) {
                        fakeSpeaker.setVolume(entry.getKey(), entry.getValue());
                    }
                    deviceManager.save(Collections.singleton(fakeSpeaker));
                })
                .doFinally(goAsync::finish)
                .subscribe(manageDevices -> {
                    Intent service = ServiceHelper.getIntent(context);
                    service.putExtra(EXTRA_DEVICE_EVENT, deviceEvent);
                    final ComponentName componentName = ServiceHelper.startService(context, service);
                    if (componentName != null) Timber.v("Service is already running.");
                }, Timber::e);
    }
}
