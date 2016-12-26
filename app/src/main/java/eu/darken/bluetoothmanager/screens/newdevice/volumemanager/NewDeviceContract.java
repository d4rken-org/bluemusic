package eu.darken.bluetoothmanager.screens.newdevice.volumemanager;

import android.bluetooth.BluetoothDevice;

import java.util.List;

import eu.darken.bluetoothmanager.util.mvp.BasePresenter;
import eu.darken.bluetoothmanager.util.mvp.BaseView;


public interface NewDeviceContract {
    interface View extends BaseView {

        void showDevices(List<BluetoothDevice> devices);

        void finishView();

    }

    interface Presenter extends BasePresenter<View> {

        void addDevice(BluetoothDevice device);

    }
}
