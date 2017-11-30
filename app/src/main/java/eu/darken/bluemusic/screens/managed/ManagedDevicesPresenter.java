package eu.darken.bluemusic.screens.managed;

import android.app.Activity;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import eu.darken.bluemusic.IAPHelper;
import eu.darken.bluemusic.core.bluetooth.BluetoothSource;
import eu.darken.bluemusic.core.database.DeviceManager;
import eu.darken.bluemusic.core.database.ManagedDevice;
import eu.darken.bluemusic.core.service.StreamHelper;
import eu.darken.ommvplib.base.Presenter;
import eu.darken.ommvplib.injection.ComponentPresenter;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.schedulers.Schedulers;

@ManagedDevicesComponent.Scope
public class ManagedDevicesPresenter extends ComponentPresenter<ManagedDevicesPresenter.View, ManagedDevicesComponent> {
    private final StreamHelper streamHelper;
    private final IAPHelper iapHelper;
    private final BluetoothSource bluetoothSource;
    private DeviceManager deviceManager;
    private Disposable deviceSub = Disposables.disposed();
    private Disposable upgradeSub = Disposables.disposed();
    private Disposable bluetoothSub = Disposables.disposed();

    @Inject
    ManagedDevicesPresenter(DeviceManager deviceManager, StreamHelper streamHelper, IAPHelper iapHelper, BluetoothSource bluetoothSource) {
        this.deviceManager = deviceManager;
        this.streamHelper = streamHelper;
        this.iapHelper = iapHelper;
        this.bluetoothSource = bluetoothSource;
    }

    @Override
    public void onBindChange(@Nullable View view) {
        super.onBindChange(view);
        if (view != null) {
            bluetoothSub = bluetoothSource.isEnabled()
                    .subscribeOn(Schedulers.computation())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(enabled -> onView(v -> v.displayBluetoothState(enabled)));

            upgradeSub = iapHelper.isProVersion()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(isProVersion -> ManagedDevicesPresenter.this.onView(v -> v.updateUpgradeState(isProVersion)));

            deviceSub = deviceManager.observe()
                    .subscribeOn(Schedulers.computation())
                    .map(managedDevices -> {
                        List<ManagedDevice> sorted = new ArrayList<>(managedDevices.values());
                        Collections.sort(sorted, (d1, d2) -> Long.compare(d2.getLastConnected(), d1.getLastConnected()));
                        return sorted;
                    })
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(view::displayDevices);
        } else {
            deviceSub.dispose();
            upgradeSub.dispose();
            bluetoothSub.dispose();
        }
    }

    void onUpdateMusicVolume(ManagedDevice device, float percentage) {
        device.setMusicVolume(percentage);
        deviceManager.save(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe(managedDevices -> {
                    if (!device.isActive()) return;
                    streamHelper.setVolume(streamHelper.getMusicId(), device.getMusicVolume(), true, 0);
                });
    }

    void onUpdateCallVolume(ManagedDevice device, float percentage) {
        device.setCallVolume(percentage);
        deviceManager.save(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe(managedDevices -> {
                    if (!device.isActive()) return;
                    streamHelper.setVolume(streamHelper.getCallId(), device.getCallVolume(), true, 0);
                });
    }

    void onDeleteDevice(ManagedDevice device) {
        deviceManager.removeDevice(device)
                .subscribeOn(Schedulers.computation())
                .subscribe();
    }

    void onEditReactionDelay(ManagedDevice device, long delay) {
        if (delay < -1) delay = -1;
        device.setActionDelay(delay == -1 ? null : delay);
        deviceManager.save(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe();
    }

    void onEditAdjustmentDelay(ManagedDevice device, long delay) {
        if (delay < -1) delay = -1;
        device.setAdjustmentDelay(delay == -1 ? null : delay);
        deviceManager.save(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe();
    }

    void onToggleMusicVolumeAction(ManagedDevice device) {
        if (device.getMusicVolume() == null) {
            device.setMusicVolume(streamHelper.getVolumePercentage(streamHelper.getMusicId()));
        } else device.setMusicVolume(null);

        deviceManager.save(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe();
    }

    void onToggleCallVolumeAction(ManagedDevice device) {
        if (device.getCallVolume() == null) {
            device.setCallVolume(streamHelper.getVolumePercentage(streamHelper.getCallId()));
        } else device.setCallVolume(null);

        deviceManager.save(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe();
    }

    void onUpgradeClicked(Activity activity) {
        iapHelper.buyProVersion(activity);
    }

    void onToggleAutoplay(ManagedDevice device) {
        device.setAutoPlayEnabled(!device.isAutoPlayEnabled());

        deviceManager.save(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe(managedDevices -> {
                });
    }

    void onRenameDevice(ManagedDevice device, String newAlias) {
        device.setAlias(newAlias);
        deviceManager.updateDevices()
                .subscribeOn(Schedulers.computation())
                .subscribe();
    }

    interface View extends Presenter.View {
        void updateUpgradeState(boolean isProVersion);

        void displayDevices(List<ManagedDevice> managedDevices);

        void displayBluetoothState(boolean enabled);
    }
}
