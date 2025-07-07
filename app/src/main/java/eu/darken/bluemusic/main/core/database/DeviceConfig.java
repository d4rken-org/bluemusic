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
    Float alarmVolume;

    boolean volumeLock;

    boolean keepAwake;

    boolean nudgeVolume;

    boolean autoplay;

    String launchPkg;

    // Getters for migration
    public String getAddress() {return address;}

    public long getLastConnected() {return lastConnected;}

    public Long getActionDelay() {return actionDelay;}

    public Long getAdjustmentDelay() {return adjustmentDelay;}

    public Long getMonitoringDuration() {return monitoringDuration;}

    public Float getMusicVolume() {return musicVolume;}

    public Float getCallVolume() {return callVolume;}

    public Float getRingVolume() {return ringVolume;}

    public Float getNotificationVolume() {return notificationVolume;}

    public Float getAlarmVolume() {return alarmVolume;}

    public boolean isVolumeLock() {return volumeLock;}

    public boolean isKeepAwake() {return keepAwake;}

    public boolean isNudgeVolume() {return nudgeVolume;}

    public boolean isAutoplay() {return autoplay;}

    public String getLaunchPkg() {return launchPkg;}
}
