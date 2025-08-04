package eu.darken.bluemusic.devices.core.database

import androidx.room.TypeConverter
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.log
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class StringListTypeConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: List<String>): String = json.encodeToString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun toStringList(value: String?): List<String> = if (value.isNullOrEmpty()) {
        emptyList()
    } else {
        try {
            json.decodeFromString(ListSerializer(String.serializer()), value)
        } catch (e: Exception) {
            log(ERROR) { "Failed to decode $value" }
            emptyList()
        }
    }
}