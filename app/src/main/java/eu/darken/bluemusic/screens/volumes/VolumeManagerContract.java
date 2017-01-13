package eu.darken.bluemusic.screens.volumes;

import java.util.List;

import eu.darken.bluemusic.core.database.ManagedDevice;
import eu.darken.bluemusic.util.mvp.BasePresenter;
import eu.darken.bluemusic.util.mvp.BaseView;


public interface VolumeManagerContract {
    interface View extends BaseView {

        void displayDevices(List<ManagedDevice> managedDevices);

    }

    interface Presenter extends BasePresenter<View> {

        void updateDeviceVolume(ManagedDevice managedDevice, float percent);

        void removeDevice(ManagedDevice managedDevice);

    }
}
