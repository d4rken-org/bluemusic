package eu.darken.bluemusic.devices.core.database

import androidx.room.TypeConverter
import eu.darken.bluemusic.monitor.core.alert.AlertType

class AlertTypeConverter {
    @TypeConverter
    fun fromAlertType(type: AlertType?): String? = type?.key

    @TypeConverter
    fun toAlertType(key: String?): AlertType = AlertType.fromKey(key)
}
