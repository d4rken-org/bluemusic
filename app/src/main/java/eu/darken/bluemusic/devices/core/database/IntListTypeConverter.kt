package eu.darken.bluemusic.devices.core.database

import androidx.room.TypeConverter
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val TAG = logTag("Database", "IntListTypeConverter")

class IntListTypeConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromIntList(value: List<Int>): String = json.encodeToString(ListSerializer(Int.serializer()), value)

    @TypeConverter
    fun toIntList(value: String?): List<Int> = if (value.isNullOrEmpty()) {
        emptyList()
    } else {
        try {
            json.decodeFromString(ListSerializer(Int.serializer()), value)
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to decode $value: ${e.asLog()}" }
            emptyList()
        }
    }
}