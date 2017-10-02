package eu.darken.bluemusic.screens.managed;

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

@ManagedDevicesComponent.Scope
public class ManagedDevicesPresenter extends ComponentPresenter<ManagedDevicesPresenter.View, ManagedDevicesComponent> {
    private final StreamHelper streamHelper;
    private DeviceManager deviceManager;
    private View view;
    private Disposable disposable;

    @Inject
    ManagedDevicesPresenter(DeviceManager deviceManager, StreamHelper streamHelper) {
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
                    streamHelper.setVolume(streamHelper.getMusicId(), device.getMusicVolume(), true, 0);
                });
    }

    void updateCallVolume(ManagedDevice device, float percentage) {
        device.setCallVolume(percentage);
        deviceManager.update(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe(managedDevices -> {
                    if (!device.isActive()) return;
                    streamHelper.setVolume(streamHelper.getCallId(), device.getCallVolume(), true, 0);
                });
    }

    void deleteDevice(ManagedDevice device) {
        deviceManager.removeDevice(device)
                .subscribeOn(Schedulers.computation())
                .subscribe();
    }

    void editReactionDelay(ManagedDevice device, long delay) {
        if (delay < -1) delay = -1;
        device.setActionDelay(delay == -1 ? null : delay);
        deviceManager.update(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe();
    }

    void editAdjustmentDelay(ManagedDevice device, long delay) {
        if (delay < -1) delay = -1;
        device.setAdjustmentDelay(delay == -1 ? null : delay);
        deviceManager.update(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe();
    }

    void toggleMusicVolumeAction(ManagedDevice device) {
        if (device.getMusicVolume() == null) {
            device.setMusicVolume(streamHelper.getVolumePercentage(streamHelper.getMusicId()));
        } else device.setMusicVolume(null);

        deviceManager.update(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe(managedDevices -> {
                });
    }

    void toggleCallVolumeAction(ManagedDevice device) {
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