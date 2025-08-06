package eu.darken.bluemusic.devices.core.database

import androidx.room.TypeConverter
import eu.darken.bluemusic.monitor.core.audio.DndMode

class DndModeTypeConverter {
    @TypeConverter
    fun fromDndMode(mode: DndMode?): String? = mode?.key

    @TypeConverter
    fun toDndMode(key: String?): DndMode? = DndMode.fromKey(key)
}