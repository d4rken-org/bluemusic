package eu.darken.bluemusic.core.database;


import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class DeviceConfig extends RealmObject {
    @PrimaryKey
    String address;
    float volumePercentage;
}
