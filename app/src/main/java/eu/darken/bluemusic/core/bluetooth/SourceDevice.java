package eu.darken.bluemusic.core.bluetooth;


import android.bluetooth.BluetoothClass;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public interface SourceDevice extends Parcelable {
    @NonNull
    BluetoothClass getBluetoothClass();

    @Nullable
    String getName();

    @NonNull
    String getAddress();

    class Action implements Parcelable {
        public enum Type {
            CONNECTED, DISCONNECTED
        }

        private final SourceDevice sourceDevice;
        private final Type type;

        public Action(SourceDevice sourceDevice, Type type) {
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

        protected Action(Parcel in) {
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

        public static final Creator<Action> CREATOR = new Creator<Action>() {
            @Override
            public Action createFromParcel(Parcel in) {
                return new Action(in);
            }

            @Override
            public Action[] newArray(int size) {
                return new Action[size];
            }
        };


    }

}
