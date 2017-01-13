package eu.darken.bluemusic.screens.volumes;

import android.os.Bundle;

import eu.darken.bluemusic.App;
import eu.darken.bluemusic.core.database.ManagedDevice;
import eu.darken.bluemusic.core.database.ManagedDeviceRepo;


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
        view.displayDevices(managedDeviceRepo.getDevices());
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
