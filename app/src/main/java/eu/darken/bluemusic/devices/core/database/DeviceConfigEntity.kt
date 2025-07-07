package eu.darken.bluemusic.devices.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_configs")
data class DeviceConfigEntity(
    @PrimaryKey
    @ColumnInfo(name = "address")
    val address: String,
    
    @ColumnInfo(name = "last_connected")
    val lastConnected: Long = 0L,
    
    @ColumnInfo(name = "action_delay")
    val actionDelay: Long? = null,
    
    @ColumnInfo(name = "adjustment_delay")
    val adjustmentDelay: Long? = null,
    
    @ColumnInfo(name = "monitoring_duration")
    val monitoringDuration: Long? = null,
    
    @ColumnInfo(name = "music_volume")
    val musicVolume: Float? = null,
    
    @ColumnInfo(name = "call_volume")
    val callVolume: Float? = null,
    
    @ColumnInfo(name = "ring_volume")
    val ringVolume: Float? = null,
    
    @ColumnInfo(name = "notification_volume")
    val notificationVolume: Float? = null,
    
    @ColumnInfo(name = "alarm_volume")
    val alarmVolume: Float? = null,
    
    @ColumnInfo(name = "volume_lock")
    val volumeLock: Boolean = false,
    
    @ColumnInfo(name = "keep_awake")
    val keepAwake: Boolean = false,
    
    @ColumnInfo(name = "nudge_volume")
    val nudgeVolume: Boolean = false,
    
    @ColumnInfo(name = "autoplay")
    val autoplay: Boolean = false,
    
    @ColumnInfo(name = "launch_pkg")
    val launchPkg: String? = null,
    
    @ColumnInfo(name = "alias")
    val alias: String? = null,
    
    @ColumnInfo(name = "name")
    val name: String? = null
)