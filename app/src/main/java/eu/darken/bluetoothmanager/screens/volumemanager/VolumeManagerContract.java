package eu.darken.bluetoothmanager.screens.volumemanager;

import java.util.List;

import eu.darken.bluetoothmanager.core.device.ManagedDevice;
import eu.darken.bluetoothmanager.util.mvp.BasePresenter;
import eu.darken.bluetoothmanager.util.mvp.BaseView;


public interface VolumeManagerContract {
    interface View extends BaseView {

        void displayDevices(List<ManagedDevice> managedDevices);

    }

    interface Presenter extends BasePresenter<View> {

        void updateDeviceVolume(ManagedDevice managedDevice, float percent);

        void removeDevice(ManagedDevice managedDevice);

    }
}
