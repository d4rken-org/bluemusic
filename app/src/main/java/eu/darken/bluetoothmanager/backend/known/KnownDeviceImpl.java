package eu.darken.bluetoothmanager.backend.known;


import android.bluetooth.BluetoothDevice;

public class KnownDeviceImpl implements KnownDevice {
    private final BluetoothDevice bluetoothDevice;
    private float volumePercentage;

    public KnownDeviceImpl(BluetoothDevice bluetoothDevice) {
        this.bluetoothDevice = bluetoothDevice;
    }

    public void setVolumePercentage(int volumePercentage) {
        this.volumePercentage = volumePercentage;
    }

    @Override
    public String getAddress() {
        return bluetoothDevice.getAddress();
    }

    @Override
    public float getVolumePercentage() {
        return volumePercentage;
    }
}
