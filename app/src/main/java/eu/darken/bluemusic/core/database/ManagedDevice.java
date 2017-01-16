package eu.darken.bluemusic.core.database;


public interface ManagedDevice {

    String getName();

    String getAddress();

    float getVolumePercentage();

    void setVolumePercentage(float volumePercentage);

    long getLastConnected();

    void setLastConnected(long timestamp);

    boolean isActive();

    void setActive(boolean active);
}
