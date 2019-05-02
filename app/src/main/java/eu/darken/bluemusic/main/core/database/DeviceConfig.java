package eu.darken.bluemusic.main.core.database;


import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class DeviceConfig extends RealmObject {
    @PrimaryKey
    String address;
    long lastConnected;

    Long actionDelay;
    Long adjustmentDelay;
    Long monitoringDuration;

    Float musicVolume;
    Float callVolume;
    Float ringVolume;
    Float notificationVolume;

    boolean volumeLock;

    boolean autoplay;

    String launchPkg;
}
