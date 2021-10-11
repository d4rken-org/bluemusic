package eu.darken.bluemusic.bluetooth.ui.discover;

import android.app.Activity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import eu.darken.bluemusic.bluetooth.core.BluetoothSource;
import eu.darken.bluemusic.bluetooth.core.FakeSpeakerDevice;
import eu.darken.bluemusic.bluetooth.core.SourceDevice;
import eu.darken.bluemusic.main.core.database.DeviceManager;
import eu.darken.bluemusic.util.iap.IAPRepo;
import eu.darken.mvpbakery.base.Presenter;
import eu.darken.mvpbakery.injection.ComponentPresenter;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

@DiscoverComponent.Scope
public class DiscoverPresenter extends ComponentPresenter<DiscoverPresenter.View, DiscoverComponent> {
    private final DeviceManager deviceManager;
    private final BluetoothSource bluetoothSource;
    private final IAPRepo iapRepo;
    private Disposable upgradeSub;
    private boolean isProVersion = false;
    private int managedDevices = 0;

    @Inject
    DiscoverPresenter(DeviceManager deviceManager, BluetoothSource bluetoothSource, IAPRepo iapRepo) {
        this.deviceManager = deviceManager;
        this.bluetoothSource = bluetoothSource;
        this.iapRepo = iapRepo;
    }

    @Override
    public void onBindChange(@Nullable View view) {
        super.onBindChange(view);
        updateProState();
        updateList();
    }

    private void updateProState() {
        if (getView() != null) {
            upgradeSub = iapRepo.isProVersion()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(isProVersion -> DiscoverPresenter.this.isProVersion = isProVersion);

        } else if (upgradeSub != null) upgradeSub.dispose();
    }

    private void updateList() {
        if (getView() == null) return;
        Single
                .zip(deviceManager.devices().firstOrError(), bluetoothSource.pairedDevices().firstOrError(), (known, paired) -> {
                    managedDevices = 0;
                    final List<SourceDevice> devices = new ArrayList<>();
                    for (SourceDevice d : paired.values()) {
                        if (!known.containsKey(d.getAddress())) {
                            devices.add(d);
                        } else managedDevices++;
                    }
                    return devices;
                })
                .subscribeOn(Schedulers.io())
                .map(sourceDevices -> {
                    Collections.sort(sourceDevices, (o1, o2) -> {
                        if (o1.getAddress().equals(FakeSpeakerDevice.ADDR)) return -1;
                        else if (o2.getAddress().equals(FakeSpeakerDevice.ADDR)) return 1;
                        else return 0;
                    });
                    return sourceDevices;
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(sourceDevices -> onView(v -> v.showDevices(sourceDevices)));
    }

    void onAddDevice(SourceDevice device) {
        if (!isProVersion && managedDevices > 2) {
            onView(View::showUpgradeDialog);
        } else {
            Timber.i("Adding new device: %s", device);
            deviceManager.addNewDevice(device)
                    .doOnSubscribe(disposable -> onView(View::showProgress))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((managedDevice, throwable) -> {
                        if (throwable != null) {
                            onView(v -> v.showError(throwable));
                        } else {
                            onView(View::closeScreen);
                        }
                    });
        }
    }

    void onPurchaseUpgrade(Activity activity) {
        iapRepo.buyProVersion(activity);
    }


    public interface View extends Presenter.View {
        void showDevices(List<SourceDevice> devices);

        void showError(Throwable error);

        void showProgress();

        void showUpgradeDialog();

        void closeScreen();
    }
}
