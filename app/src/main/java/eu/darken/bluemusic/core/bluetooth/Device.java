package eu.darken.bluemusic.core.bluetooth;


import android.support.annotation.Nullable;

public interface Device {

    @Nullable
    String getName();

    String getAddress();
}
