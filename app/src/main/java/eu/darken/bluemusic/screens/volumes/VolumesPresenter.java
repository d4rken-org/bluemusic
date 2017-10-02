package eu.darken.bluemusic.screens.volumes;

import android.os.Bundle;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import eu.darken.bluemusic.core.database.DeviceManager;
import eu.darken.bluemusic.core.database.ManagedDevice;
import eu.darken.bluemusic.core.service.StreamHelper;
import eu.darken.ommvplib.base.Presenter;
import eu.darken.ommvplib.injection.ComponentPresenter;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@VolumesComponent.Scope
public class VolumesPresenter extends ComponentPresenter<VolumesPresenter.View, VolumesComponent> {
    private final StreamHelper streamHelper;
    private DeviceManager deviceManager;
    private View view;
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
    public void onBindChange(@Nullable View view) {
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

    void updateMusicVolume(ManagedDevice device, float percentage) {
        device.setMusicVolume(percentage);
        deviceManager.update(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe(managedDevices -> {
                    if (!device.isActive()) return;
                    streamHelper.setVolumeInstant(streamHelper.getMusicId(), device.getRealMusicVolume(), true);
                });
    }

    void updateCallVolume(ManagedDevice device, float percentage) {
        device.setCallVolume(percentage);
        deviceManager.update(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe(managedDevices -> {
                    if (!device.isActive()) return;
                    streamHelper.setVolumeInstant(streamHelper.getCallId(), device.getRealVoiceVolume(), true);
                });
    }

    public void deleteDevice(ManagedDevice device) {
        deviceManager.removeDevice(device)
                .subscribeOn(Schedulers.computation())
                .subscribe();
    }

    public void editDelay(ManagedDevice device, long delay) {
        if (delay < 0) delay = 0;
        device.setActionDelay(delay == 0 ? null : delay);
        deviceManager.update(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe();
    }

    public void toggleMusicVolumeAction(ManagedDevice device) {
        if (device.getMusicVolume() == null) {
            device.setMusicVolume(streamHelper.getVolumePercentage(streamHelper.getMusicId()));
        } else device.setMusicVolume(null);

        deviceManager.update(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe(managedDevices -> {
                });
    }

    public void toggleCallVolumeAction(ManagedDevice device) {
        if (device.getCallVolume() == null) {
            device.setCallVolume(streamHelper.getVolumePercentage(streamHelper.getCallId()));
        } else device.setCallVolume(null);

        deviceManager.update(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe(managedDevices -> {
                });
    }

    interface View extends Presenter.View {

        void displayDevices(List<ManagedDevice> managedDevices);

    }
}
