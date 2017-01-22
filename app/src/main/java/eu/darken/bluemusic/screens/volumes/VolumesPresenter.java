package eu.darken.bluemusic.screens.volumes;

import android.media.AudioManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import eu.darken.bluemusic.core.database.DeviceManager;
import eu.darken.bluemusic.core.database.ManagedDevice;
import eu.darken.bluemusic.core.service.StreamHelper;
import eu.darken.ommvplib.injection.ComponentPresenter;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@VolumesScope
public class VolumesPresenter extends ComponentPresenter<VolumesView, VolumesComponent> {
    private final StreamHelper streamHelper;
    private DeviceManager deviceManager;
    private VolumesView view;
    private Disposable disposable;

    @Inject
    VolumesPresenter(DeviceManager deviceManager, StreamHelper streamHelper) {
        this.deviceManager = deviceManager;
        this.streamHelper = streamHelper;
    }

    @Override
    public void onCreate(Bundle bundle) {

    }

    @Override
    public void onBindChange(@Nullable VolumesView view) {
        this.view = view;
        if (view != null) {
            disposable = deviceManager.observe()
                    .subscribeOn(Schedulers.computation())
                    .map(managedDevices -> {
                        List<ManagedDevice> sorted = new ArrayList<>(managedDevices.values());
                        Collections.sort(sorted, (d1, d2) -> Long.compare(d2.getLastConnected(), d1.getLastConnected()));
                        return sorted;
                    })
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(view::displayDevices);
        } else {
            if (disposable != null) disposable.dispose();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle bundle) {

    }

    @Override
    public void onDestroy() {

    }

    void updateMusicVolume(ManagedDevice device, float percent) {
        device.setMusicVolume(percent);
        deviceManager.update(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe(managedDevices -> {
                    if (!device.isActive()) return;
                    streamHelper.setStreamVolume(streamHelper.getMusicId(), device.getRealMusicVolume(), AudioManager.FLAG_SHOW_UI);
                });
    }

    void updateVoiceVolume(ManagedDevice device, float percentage) {
        device.setVoiceVolume(percentage);
        deviceManager.update(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe(managedDevices -> {
                    if (!device.isActive()) return;
                    streamHelper.setStreamVolume(streamHelper.getVoiceId(), device.getRealVoiceVolume(), AudioManager.FLAG_SHOW_UI);
                });
    }
}
