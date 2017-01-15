package eu.darken.bluemusic.screens.volumes;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import javax.inject.Inject;

import eu.darken.bluemusic.App;
import eu.darken.bluemusic.core.database.ManagedDevice;
import eu.darken.bluemusic.core.database.ManagedDeviceRepo;
import eu.darken.ommvplib.injection.ComponentPresenter;

@VolumeManagerScope
public class VolumeManagerPresenter extends ComponentPresenter<VolumeManagerView, VolumeManagerComponent> {
    static final String TAG = App.LOGPREFIX + "IntroPresenter";
    @Inject ManagedDeviceRepo managedDeviceRepo;
    private VolumeManagerView view;

    @Inject
    VolumeManagerPresenter() {
    }

    @Override
    public void onCreate(Bundle bundle) {

    }

    @Override
    public void onBindChange(@Nullable VolumeManagerView view) {
        this.view = view;
        if (view != null) view.displayDevices(managedDeviceRepo.getDevices());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle bundle) {

    }

    @Override
    public void onDestroy() {

    }

    public void updateDeviceVolume(ManagedDevice managedDevice, float percent) {

    }

    public void removeDevice(ManagedDevice managedDevice) {

    }
}
