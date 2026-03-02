package eu.darken.bluemusic.common.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

@PublishedApi
internal inline fun <reified T> kotlinxSerializationReader(
    json: Json,
    defaultValue: T,
    onErrorFallbackToDefault: Boolean = false,
): (Any?) -> T {
    val serializer = json.serializersModule.serializer<T>()
    return { rawValue ->
        rawValue as String?
        if (rawValue == null) {
            defaultValue
        } else {
            val decoder = { json.decodeFromString(serializer, rawValue) }
            if (onErrorFallbackToDefault) {
                try {
                    decoder()
                } catch (e: Exception) {
                    log(TAG, WARN) { "Deserialization failed, using default: ${e.message}" }
                    defaultValue
                }
            } else {
                decoder()
            }
        }
    }
}

inline fun <reified T> kotlinxSerializationWriter(
    json: Json,
): (T) -> Any? {
    val serializer = json.serializersModule.serializer<T>()
    return { newValue: T ->
        newValue?.let { json.encodeToString(serializer, it) }
    }
}

inline fun <reified T : Any?> DataStore<Preferences>.createValue(
    key: String,
    defaultValue: T = null as T,
    json: Json,
    onErrorFallbackToDefault: Boolean = false,
) = DataStoreValue(
    dataStore = this,
    key = stringPreferencesKey(key),
    reader = kotlinxSerializationReader(json, defaultValue, onErrorFallbackToDefault),
    writer = kotlinxSerializationWriter(json),
)

@PublishedApi
internal val TAG = logTag("DataStore", "Value", "Serialization")
