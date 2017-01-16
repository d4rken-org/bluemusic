package eu.darken.bluemusic.core.bluetooth;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public interface Device {

    @Nullable
    String getName();

    @NonNull
    String getAddress();
}
