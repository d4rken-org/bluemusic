package eu.darken.bluemusic.core.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import static eu.darken.bluemusic.core.bluetooth.BluetoothEventReceiver.EXTRA_DEVICE_ADDRESS;

public class FakeDeviceSource implements BluetoothSource {

    private final Context context;
    private final Handler handler;
    private final Collection<Device> devices = new ArrayList<>();

    public FakeDeviceSource(Context context) {
        this.context = context;
        HandlerThread thread = new HandlerThread("FakeDeviceSourceThread");
        thread.start();
        handler = new Handler(thread.getLooper());
        for (int i = 0; i < 10; i++) {
            final String name = "Device " + i;
            final String address = UUID.randomUUID().toString();
            final Device device = new Device() {
                @Nullable
                @Override
                public String getName() {
                    return name;
                }

                @NonNull
                @Override
                public String getAddress() {
                    return address;
                }

                @Override
                public String toString() {
                    return String.format(Locale.US, "Device(name=%s, address=%s)", getName(), getAddress());
                }
            };
            devices.add(device);
            handler.postDelayed(makeDeviceConnected(device), randTime());
        }
    }

    private static long randTime() {
        long lower = 3 * 1000; //assign lower range value
        long upper = 30 * 1000; //assign upper range value
        Random random = new Random();


        return lower + (long) (random.nextDouble() * (upper - lower));
    }

    private Runnable makeDeviceConnected(Device device) {
        return () -> {
            Intent intent = new Intent();
            intent.setAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            intent.putExtra(EXTRA_DEVICE_ADDRESS, device.getAddress());
            new BluetoothEventReceiver().onReceive(context, intent);
            handler.postDelayed(makeDeviceDisconnected(device), randTime());
        };
    }

    private Runnable makeDeviceDisconnected(Device device) {
        return () -> {
            Intent intent = new Intent();
            intent.setAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            intent.putExtra(EXTRA_DEVICE_ADDRESS, device.getAddress());
            new BluetoothEventReceiver().onReceive(context, intent);
            handler.postDelayed(makeDeviceConnected(device), randTime());
        };
    }

    @Override
    public Collection<Device> getPairedDevices() {
        return devices;
    }

    @Override
    public Collection<Device> getConnectedDevices(int profile) {
        return devices;
    }
}
