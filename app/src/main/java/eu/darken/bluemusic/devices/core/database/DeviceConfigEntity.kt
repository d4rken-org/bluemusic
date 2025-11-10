package eu.darken.bluemusic.devices.core.database

import android.view.KeyEvent
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import eu.darken.bluemusic.monitor.core.audio.DndMode

@Entity(tableName = "device_configs")
data class DeviceConfigEntity(
    @PrimaryKey
    @ColumnInfo(name = "address")
    val address: String,

    @ColumnInfo(name = "custom_name")
    val customName: String? = null,

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

    @ColumnInfo(name = "volume_observing")
    val volumeObserving: Boolean = false,

    @ColumnInfo(name = "volume_rate_limiter")
    val volumeRateLimiter: Boolean = false,

    @ColumnInfo(name = "volume_rate_limit_increase_ms")
    val volumeRateLimitIncreaseMs: Long? = null,

    @ColumnInfo(name = "volume_rate_limit_decrease_ms")
    val volumeRateLimitDecreaseMs: Long? = null,

    @ColumnInfo(name = "volume_save_on_disconnect", defaultValue = "false")
    val volumeSaveOnDisconnect: Boolean = false,
    
    @ColumnInfo(name = "keep_awake")
    val keepAwake: Boolean = false,
    
    @ColumnInfo(name = "nudge_volume")
    val nudgeVolume: Boolean = false,
    
    @ColumnInfo(name = "autoplay")
    val autoplay: Boolean = false,

    @ColumnInfo(name = "launch_pkgs", defaultValue = "[]")
    val launchPkgs: List<String> = emptyList(),

    @ColumnInfo(name = "show_home_screen", defaultValue = "false")
    val showHomeScreen: Boolean = false,

    @ColumnInfo(name = "autoplay_keycodes", defaultValue = "[126]") // 126 = KEYCODE_MEDIA_PLAY
    val autoplayKeycodes: List<Int> = listOf(KeyEvent.KEYCODE_MEDIA_PLAY),

    @ColumnInfo(name = "is_enabled", defaultValue = "true")
    val isEnabled: Boolean = true,

    @ColumnInfo(name = "visible_adjustments", defaultValue = "true")
    val visibleAdjustments: Boolean? = true,

    @ColumnInfo(name = "dnd_mode")
    val dndMode: DndMode? = null,
)