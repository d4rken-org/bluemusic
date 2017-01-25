package eu.darken.bluemusic.core.bluetooth;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Locale;

class SourceDeviceWrapper implements SourceDevice {
    private final BluetoothDevice realDevice;

    SourceDeviceWrapper(BluetoothDevice realDevice) {
        this.realDevice = realDevice;
    }

    @Nullable
    @Override
    public String getName() {
        return realDevice.getName();
    }

    @NonNull
    @Override
    public String getAddress() {
        return realDevice.getAddress();
    }

    @Nullable
    @Override
    public BluetoothClass getBluetoothClass() {
        return realDevice.getBluetoothClass();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SourceDeviceWrapper that = (SourceDeviceWrapper) o;

        return getAddress().equals(that.getAddress());
    }

    @Override
    public int hashCode() {
        return getAddress().hashCode();
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "Device(name=%s, address=%s)", getName(), getAddress());
    }


    protected SourceDeviceWrapper(Parcel in) {
        realDevice = in.readParcelable(BluetoothDevice.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(realDevice, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<SourceDeviceWrapper> CREATOR = new Creator<SourceDeviceWrapper>() {
        @Override
        public SourceDeviceWrapper createFromParcel(Parcel in) {
            return new SourceDeviceWrapper(in);
        }

        @Override
        public SourceDeviceWrapper[] newArray(int size) {
            return new SourceDeviceWrapper[size];
        }
    };
}
