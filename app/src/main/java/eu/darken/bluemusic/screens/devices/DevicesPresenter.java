package eu.darken.bluemusic.screens.devices;

import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import eu.darken.bluemusic.core.bluetooth.BluetoothSource;
import eu.darken.bluemusic.core.bluetooth.SourceDevice;
import eu.darken.bluemusic.core.database.DeviceManager;
import eu.darken.ommvplib.base.Presenter;
import eu.darken.ommvplib.injection.ComponentPresenter;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

@DevicesComponent.Scope
public class DevicesPresenter extends ComponentPresenter<DevicesPresenter.View, DevicesComponent> {
    private final DeviceManager deviceManager;
    private final BluetoothSource bluetoothSource;

    @Inject
    DevicesPresenter(DeviceManager deviceManager, BluetoothSource bluetoothSource) {
        this.deviceManager = deviceManager;
        this.bluetoothSource = bluetoothSource;
    }

    @Override
    public void onBindChange(@Nullable View view) {
        super.onBindChange(view);
        if (view != null) {
            updateList();
        }
    }

    private void updateList() {
        Single.zip(
                deviceManager.loadDevices(false),
                bluetoothSource.getPairedDevices(),
                (known, paired) -> {
                    List<SourceDevice> devices = new ArrayList<>();
                    for (SourceDevice d : paired.values()) {
                        if (!known.containsKey(d.getAddress())) devices.add(d);
                    }
                    return devices;
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(sourceDevices -> onView(v -> v.showDevices(sourceDevices)));
    }

    void onAddDevice(SourceDevice device) {
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

    public interface View extends Presenter.View {
        void showDevices(List<SourceDevice> devices);

        void showError(Throwable error);

        void showProgress();

        void closeScreen();
    }
}
