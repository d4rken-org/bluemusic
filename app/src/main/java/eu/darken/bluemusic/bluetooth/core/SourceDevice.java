package eu.darken.bluemusic.bluetooth.core;


import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import eu.darken.bluemusic.main.core.audio.AudioStream;
import timber.log.Timber;

public interface SourceDevice extends Parcelable {
    @Nullable
    BluetoothClass getBluetoothClass();

    @Nullable
    String getName();

    String getAddress();

    boolean setAlias(String newAlias);

    @Nullable
    String getAlias();

    String getLabel();

    AudioStream.Id getStreamId(AudioStream.Type type);

    class Event implements Parcelable {
        public enum Type {
            CONNECTED, DISCONNECTED
        }

        private final Type type;
        private final SourceDevice device;

        public Event(SourceDevice device, Type type) {
            this.device = device;
            this.type = type;
        }

        public String getAddress() {
            return device.getAddress();
        }

        @NonNull
        public SourceDevice getDevice() {
            return device;
        }

        @NonNull
        public Type getType() {
            return type;
        }

        @Override
        public String toString() {
            return "SourceDevice.Event(type=" + type + ", device=" + device + ")";
        }

        protected Event(Parcel in) {
            device = in.readParcelable(SourceDevice.class.getClassLoader());
            type = Type.valueOf(in.readString());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeParcelable(device, flags);
            dest.writeString(type.name());
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<Event> CREATOR = new Creator<Event>() {
            @Override
            public Event createFromParcel(Parcel in) {
                return new Event(in);
            }

            @Override
            public Event[] newArray(int size) {
                return new Event[size];
            }
        };

        @Nullable
        public static Event createEvent(Intent intent) {
            final BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (bluetoothDevice == null) {
                Timber.w("Intent didn't contain a bluetooth device!");
                return null;
            }
            SourceDevice sourceDevice = new SourceDeviceWrapper(bluetoothDevice);

            SourceDevice.Event.Type actionType;
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) {
                actionType = SourceDevice.Event.Type.CONNECTED;
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
                actionType = SourceDevice.Event.Type.DISCONNECTED;
            } else {
                Timber.w("Invalid action: %s", intent.getAction());
                return null;
            }

            try {
                Timber.d("Device: %s | Action: %s", sourceDevice, actionType);
            } catch (Exception e) {
                Timber.e(e);
                return null;
            }

            return new SourceDevice.Event(sourceDevice, actionType);
        }

    }

}
