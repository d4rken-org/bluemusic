package eu.darken.bluetoothmanager.screens.volumemanager;

import android.os.Bundle;

import eu.darken.bluetoothmanager.App;
import eu.darken.bluetoothmanager.backend.known.KnownDevice;
import eu.darken.bluetoothmanager.backend.known.KnownDeviceRepository;


public class VolumeManagerPresenter implements VolumeManagerContract.Presenter {
    static final String TAG = App.LOGPREFIX + "IntroPresenter";
    private final KnownDeviceRepository knownDeviceRepository;
    VolumeManagerContract.View view;

    public VolumeManagerPresenter(KnownDeviceRepository knownDeviceRepository) {
        this.knownDeviceRepository = knownDeviceRepository;
    }

    @Override
    public void onCreate(Bundle bundle) {

    }

    @Override
    public void onAttachView(final VolumeManagerContract.View view) {
        this.view = view;
        view.displayDevices(knownDeviceRepository.get());
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
    public void updateDeviceVolume(KnownDevice knownDevice, float percent) {

    }

    @Override
    public void removeDevice(KnownDevice knownDevice) {

    }
}
