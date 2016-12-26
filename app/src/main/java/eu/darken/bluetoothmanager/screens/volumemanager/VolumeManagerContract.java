package eu.darken.bluetoothmanager.screens.volumemanager;

import java.util.List;

import eu.darken.bluetoothmanager.backend.known.KnownDevice;
import eu.darken.bluetoothmanager.util.mvp.BasePresenter;
import eu.darken.bluetoothmanager.util.mvp.BaseView;


public interface VolumeManagerContract {
    interface View extends BaseView {

        void displayDevices(List<KnownDevice> knownDevices);

    }

    interface Presenter extends BasePresenter<View> {

        void updateDeviceVolume(KnownDevice knownDevice, float percent);

        void removeDevice(KnownDevice knownDevice);

    }
}
