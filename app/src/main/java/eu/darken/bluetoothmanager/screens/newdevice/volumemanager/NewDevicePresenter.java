package eu.darken.bluetoothmanager.screens.newdevice.volumemanager;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Iterator;

import eu.darken.bluetoothmanager.App;
import eu.darken.bluetoothmanager.backend.known.KnownDevice;
import eu.darken.bluetoothmanager.backend.known.KnownDeviceRepository;
import eu.darken.bluetoothmanager.backend.live.DeviceSource;


public class NewDevicePresenter implements NewDeviceContract.Presenter {
    static final String TAG = App.LOGPREFIX + "IntroPresenter";
    NewDeviceContract.View view;
    final DeviceSource deviceSource;
    private final KnownDeviceRepository knownRepo;

    public NewDevicePresenter(DeviceSource deviceSource, KnownDeviceRepository knownRepo) {
        this.deviceSource = deviceSource;
        this.knownRepo = knownRepo;
    }

    @Override
    public void onCreate(Bundle bundle) {

    }

    @Override
    public void onAttachView(final NewDeviceContract.View view) {
        this.view = view;
        final ArrayList<BluetoothDevice> devices = new ArrayList<>(deviceSource.getPairedDevices());
        final Iterator<BluetoothDevice> iterator = devices.iterator();
        while (iterator.hasNext()) {
            final BluetoothDevice device = iterator.next();
            for (KnownDevice knownDevice : knownRepo.get()) {
                if (device.getAddress().equals(knownDevice.getAddress())) {
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
