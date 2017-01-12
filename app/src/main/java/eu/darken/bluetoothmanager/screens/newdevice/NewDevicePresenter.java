package eu.darken.bluetoothmanager.screens.newdevice;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Iterator;

import eu.darken.bluetoothmanager.App;
import eu.darken.bluetoothmanager.core.manager.BluetoothSource;
import eu.darken.bluetoothmanager.core.device.ManagedDevice;
import eu.darken.bluetoothmanager.core.device.ManagedDeviceRepo;


public class NewDevicePresenter implements NewDeviceContract.Presenter {
    static final String TAG = App.LOGPREFIX + "IntroPresenter";
    NewDeviceContract.View view;
    final BluetoothSource bluetoothSource;
    private final ManagedDeviceRepo knownRepo;

    public NewDevicePresenter(BluetoothSource bluetoothSource, ManagedDeviceRepo knownRepo) {
        this.bluetoothSource = bluetoothSource;
        this.knownRepo = knownRepo;
    }

    @Override
    public void onCreate(Bundle bundle) {

    }

    @Override
    public void onAttachView(final NewDeviceContract.View view) {
        this.view = view;
        final ArrayList<BluetoothDevice> devices = new ArrayList<>(bluetoothSource.getPairedDevices());
        final Iterator<BluetoothDevice> iterator = devices.iterator();
        while (iterator.hasNext()) {
            final BluetoothDevice device = iterator.next();
            for (ManagedDevice managedDevice : knownRepo.get()) {
                if (device.getAddress().equals(managedDevice.getAddress())) {
                    iterator.remove();
                    break;
                }
            }
        }
        view.showDevices(devices);
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
    public void addDevice(BluetoothDevice device) {
        knownRepo.put(device);
        view.finishView();
    }
}
