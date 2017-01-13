package eu.darken.bluemusic.core.database;


public interface ManagedDevice {

    String getName();

    String getAddress();

    float getVolumePercentage();

    void setVolumePercentage(float volumePercentage);
}
