package eu.darken.bluemusic.bluetooth.core;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.lang.reflect.Method;
import java.util.Locale;

import eu.darken.bluemusic.main.core.audio.AudioStream;
import timber.log.Timber;

class SourceDeviceWrapper implements SourceDevice {
    private final BluetoothDevice realDevice;

    SourceDeviceWrapper(BluetoothDevice realDevice) {
        this.realDevice = realDevice;
    }

    @Override
    public String getLabel() {
        String label = getAlias();
        if (label == null) label = getName();
        if (label == null) label = getAddress();
        return label;
    }

    @Override
    public boolean setAlias(String alias) {
        try {
            //noinspection JavaReflectionMemberAccess
            Method method = realDevice.getClass().getMethod("setAlias", String.class);
            return (boolean) method.invoke(realDevice, alias);
        } catch (Exception e) {
            Timber.e(e);
        }
        return false;
    }

    @Nullable
    @Override
    public String getAlias() {
        try {
            //noinspection JavaReflectionMemberAccess
            Method method = realDevice.getClass().getMethod("getAliasName");
            return (String) method.invoke(realDevice);
        } catch (Exception e) {
            Timber.e(e);
        }
        return null;
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
    public AudioStream.Id getStreamId(AudioStream.Type type) {
        switch (type) {
            case MUSIC:
                return AudioStream.Id.STREAM_MUSIC;
            case CALL:
                return AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE;
            case RINGTONE:
                return AudioStream.Id.STREAM_RINGTONE;
            case NOTIFICATION:
                return AudioStream.Id.STREAM_NOTIFICATION;
            default:
                throw new IllegalArgumentException("Unsupported AudioStreamType: " + type);
        }
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
