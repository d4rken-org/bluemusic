package eu.darken.bluemusic.core.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;

import static eu.darken.bluemusic.core.bluetooth.BluetoothEventReceiver.EXTRA_DEVICE_ADDRESS;

public class FakeDeviceSource implements BluetoothSource {

    public FakeDeviceSource(Context context) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Intent intent = new Intent();
                intent.setAction(BluetoothDevice.ACTION_ACL_CONNECTED);
                intent.putExtra(EXTRA_DEVICE_ADDRESS, "ad:dr:es");
                context.sendBroadcast(intent);
            }
        }).start();
    }

    @Override
    public Collection<Device> getPairedDevices() {
        Collection<Device> devices = new ArrayList<>();
        devices.add(new Device() {
            @Nullable
            @Override
            public String getName() {
                return "Device 1";
            }

            @Override
            public String getAddress() {
                return "ad:dr:es";
            }
        });
        return devices;
    }

}
