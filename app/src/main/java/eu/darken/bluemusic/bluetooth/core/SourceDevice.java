package eu.darken.bluemusic.bluetooth.core;


import android.bluetooth.BluetoothClass;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public interface SourceDevice extends Parcelable {
    @Nullable
    BluetoothClass getBluetoothClass();

    @Nullable
    String getName();

    @NonNull
    String getAddress();

    boolean setAlias(String newAlias);

    @Nullable
    String getAlias();

    class Event implements Parcelable {
        public enum Type {
            CONNECTED, DISCONNECTED
        }

        private final SourceDevice sourceDevice;
        private final Type type;

        public Event(SourceDevice sourceDevice, Type type) {
            this.sourceDevice = sourceDevice;
            this.type = type;
        }

        public String getAddress() {
            return sourceDevice.getAddress();
        }

        @NonNull
        public SourceDevice getDevice() {
            return sourceDevice;
        }

        @NonNull
        public Type getType() {
            return type;
        }

        protected Event(Parcel in) {
            sourceDevice = in.readParcelable(SourceDevice.class.getClassLoader());
            type = Type.valueOf(in.readString());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeParcelable(sourceDevice, flags);
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


    }

}
