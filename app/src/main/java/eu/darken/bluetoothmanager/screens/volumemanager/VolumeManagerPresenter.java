package eu.darken.bluetoothmanager.screens.volumemanager;

import android.os.Bundle;

import eu.darken.bluetoothmanager.App;
import eu.darken.bluetoothmanager.core.device.ManagedDevice;
import eu.darken.bluetoothmanager.core.device.ManagedDeviceRepo;


public class VolumeManagerPresenter implements VolumeManagerContract.Presenter {
    static final String TAG = App.LOGPREFIX + "IntroPresenter";
    private final ManagedDeviceRepo managedDeviceRepo;
    VolumeManagerContract.View view;

    public VolumeManagerPresenter(ManagedDeviceRepo managedDeviceRepo) {
        this.managedDeviceRepo = managedDeviceRepo;
    }

    @Override
    public void onCreate(Bundle bundle) {

    }

    @Override
    public void onAttachView(final VolumeManagerContract.View view) {
        this.view = view;
        view.displayDevices(managedDeviceRepo.get());
    }

    @Override
    public void onDetachView() {
        view = null;
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {

    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void updateDeviceVolume(ManagedDevice managedDevice, float percent) {

    }

    @Override
    public void removeDevice(ManagedDevice managedDevice) {

    }
}
