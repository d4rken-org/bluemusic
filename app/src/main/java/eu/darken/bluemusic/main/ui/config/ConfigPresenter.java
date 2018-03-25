package eu.darken.bluemusic.main.ui.config;

import android.app.Activity;
import android.app.NotificationManager;
import android.support.annotation.Nullable;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import eu.darken.bluemusic.IAPHelper;
import eu.darken.bluemusic.main.core.audio.AudioStream;
import eu.darken.bluemusic.main.core.audio.StreamHelper;
import eu.darken.bluemusic.main.core.database.DeviceManager;
import eu.darken.bluemusic.main.core.database.ManagedDevice;
import eu.darken.bluemusic.settings.core.Settings;
import eu.darken.bluemusic.util.ApiHelper;
import eu.darken.bluemusic.util.AppTool;
import eu.darken.mvpbakery.base.Presenter;
import eu.darken.mvpbakery.injection.ComponentPresenter;
import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

@ConfigComponent.Scope
public class ConfigPresenter extends ComponentPresenter<ConfigPresenter.View, ConfigComponent> {
    private final DeviceManager deviceManager;
    private final IAPHelper iapHelper;
    private final AppTool appTool;
    private final NotificationManager notificationManager;
    private final StreamHelper streamHelper;
    private Disposable upgradeSub;
    private Disposable updateSub;
    private boolean isProVersion = false;
    private String deviceAddress;
    private ManagedDevice device;

    @Inject
    ConfigPresenter(DeviceManager deviceManager,
                    StreamHelper streamHelper,
                    IAPHelper iapHelper,
                    AppTool appTool,
                    NotificationManager notificationManager
    ) {
        this.deviceManager = deviceManager;
        this.streamHelper = streamHelper;
        this.iapHelper = iapHelper;
        this.appTool = appTool;
        this.notificationManager = notificationManager;
    }

    public void setDevice(String address) {
        this.deviceAddress = address;
    }

    @Override
    public void onBindChange(@Nullable View view) {
        super.onBindChange(view);
        updatePro();
        updateDevice();
    }

    private void updateDevice() {
        if (getView() != null) {
            updateSub = deviceManager.observe()
                    .subscribeOn(Schedulers.io())
                    .map(deviceMap -> {
                        for (ManagedDevice d : deviceMap.values()) {
                            if (d.getAddress().equals(deviceAddress)) return d;
                        }
                        throw new IllegalStateException();
                    })
                    .doOnNext(d -> Timber.d("Updating device: %s", d))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(device -> {
                        ConfigPresenter.this.device = device;
                        onView(v -> v.updateDevice(device));
                    }, e -> onView(View::finishScreen));
        } else if (updateSub != null) updateSub.dispose();
    }

    private void updatePro() {
        if (getView() != null) {
            iapHelper.check();
            upgradeSub = iapHelper.isProVersion()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(isProVersion -> {
                        ConfigPresenter.this.isProVersion = isProVersion;
                        onView(v -> v.updateProState(isProVersion));
                    });
        } else if (upgradeSub != null) upgradeSub.dispose();
    }

    void onPurchaseUpgrade(Activity activity) {
        iapHelper.buyProVersion(activity);
    }

    void onToggleMusicVolume() {
        if (device.getVolume(AudioStream.Type.MUSIC) == null) {
            device.setVolume(AudioStream.Type.MUSIC, streamHelper.getVolumePercentage(device.getStreamId(AudioStream.Type.MUSIC)));
        } else device.setVolume(AudioStream.Type.MUSIC, null);

        deviceManager.save(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe();
    }

    void onToggleCallVolume() {
        if (device.getVolume(AudioStream.Type.CALL) == null) {
            device.setVolume(AudioStream.Type.CALL, streamHelper.getVolumePercentage(device.getStreamId(AudioStream.Type.CALL)));
        } else device.setVolume(AudioStream.Type.CALL, null);

        deviceManager.save(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe();
    }

    void onToggleRingVolume() {
        if (device.getVolume(AudioStream.Type.RINGTONE) == null) {
            if (ApiHelper.hasMarshmallow() && !notificationManager.isNotificationPolicyAccessGranted()) {
                // Missing permissions for this, reset value
                onView(View::showNotificationPermissionView);
            }
            device.setVolume(AudioStream.Type.RINGTONE, streamHelper.getVolumePercentage(device.getStreamId(AudioStream.Type.RINGTONE)));
        } else {
            device.setVolume(AudioStream.Type.RINGTONE, null);
        }

        deviceManager.save(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe();
    }

    boolean onToggleAutoPlay() {
        if (isProVersion) {
            device.setAutoPlayEnabled(!device.isAutoPlayEnabled());
            deviceManager.save(Collections.singleton(device))
                    .subscribeOn(Schedulers.computation())
                    .subscribe(managedDevices -> { });
        } else {
            onView(View::showRequiresPro);
        }
        return device.isAutoPlayEnabled();
    }

    void onEditReactionDelayClicked() {
        long delay = device.getActionDelay() != null ? device.getActionDelay() : Settings.DEFAULT_REACTION_DELAY;
        onView(v -> v.showReactionDelayDialog(delay));
    }

    void onEditReactionDelay(long delay) {
        if (delay < -1) delay = -1;
        device.setActionDelay(delay == -1 ? null : delay);
        deviceManager.save(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe();
    }

    void onEditAdjustmentDelayClicked() {
        long delay = device.getAdjustmentDelay() != null ? device.getAdjustmentDelay() : Settings.DEFAULT_ADJUSTMENT_DELAY;
        onView(v -> v.showAdjustmentDelayDialog(delay));
    }

    void onEditAdjustmentDelay(long delay) {
        if (delay < -1) delay = -1;
        device.setAdjustmentDelay(delay == -1 ? null : delay);
        deviceManager.save(Collections.singleton(device))
                .subscribeOn(Schedulers.computation())
                .subscribe();
    }

    public void onRenameClicked() {
        if (isProVersion) onView(v -> v.showRenameDialog(device.getAlias()));
        else onView(View::showRequiresPro);
    }

    void onRenameDevice(String newAlias) {
        if (newAlias == null) device.setAlias(device.getName());
        else device.setAlias(newAlias);
        deviceManager.updateDevices()
                .subscribeOn(Schedulers.io())
                .subscribe();
    }

    void onDeleteDevice() {
        deviceManager.removeDevice(device)
                .subscribeOn(Schedulers.io())
                .subscribe();
    }

    public void onLaunchAppClicked() {
        if (isProVersion) {
            Single.create((SingleOnSubscribe<List<AppTool.Item>>) e -> e.onSuccess(appTool.getApps()))
                    .map(apps -> {
                        apps.add(0, AppTool.Item.empty());
                        return apps;
                    })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(apps -> onView(v -> v.showAppSelectionDialog(apps)));
        } else {
            onView(View::showRequiresPro);
        }
    }

    public void onLaunchAppSelected(AppTool.Item item) {
        device.setLaunchPkg(item.getPackageName());
        deviceManager.save(Collections.singleton(device))
                .subscribeOn(Schedulers.io())
                .subscribe();
    }


    public interface View extends Presenter.View {
        void updateProState(boolean isPro);

        void updateDevice(ManagedDevice devices);

        void showRequiresPro();

        void showReactionDelayDialog(long delay);

        void showAppSelectionDialog(List<AppTool.Item> items);

        void showAdjustmentDelayDialog(long delay);

        void showRenameDialog(String current);

        void finishScreen();

        void showNotificationPermissionView();
    }
}
