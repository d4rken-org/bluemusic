package eu.darken.bluemusic.devices.core

import android.content.Context
import android.view.KeyEvent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.datastore.PreferenceData
import eu.darken.bluemusic.common.datastore.createValue
import eu.darken.bluemusic.common.debug.logging.logTag
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DevicesSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
    json: Json,
) : PreferenceData {

    private val Context.dataStore by preferencesDataStore(name = "settings_devices")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val isEnabled = dataStore.createValue("devices.enabled", true)
    val visibleAdjustments = dataStore.createValue("devices.volume.adjustments.visible", true)
    val restoreOnBoot = dataStore.createValue("devices.volume.restore.boot.enabled", true)
    val autoplayKeycodes = dataStore.createValue("devices.autoplay.keycodes", listOf(KeyEvent.KEYCODE_MEDIA_PLAY), json)

    companion object {
        internal val TAG = logTag("Devices", "Settings")
    }
}