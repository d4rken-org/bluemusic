package eu.darken.bluemusic.devices.core

import android.content.Context
import android.view.KeyEvent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.datastore.PreferenceScreenData
import eu.darken.bluemusic.common.datastore.PreferenceStoreMapper
import eu.darken.bluemusic.common.datastore.createValue
import eu.darken.bluemusic.common.debug.DebugSettings
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.theming.ThemeMode
import eu.darken.bluemusic.common.theming.ThemeStyle
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DevicesSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
    debugSettings: DebugSettings,
    json: Json,
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "settings_devices")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val themeMode = dataStore.createValue("core.ui.theme.mode", ThemeMode.SYSTEM, json)
    val themeStyle = dataStore.createValue("core.ui.theme.style", ThemeStyle.DEFAULT, json)

    val isEnabled = dataStore.createValue("devices.enabled", true)
    val visibleAdjustments = dataStore.createValue("devices.volume.adjustments.visible", true)
    val restoreOnBoot = dataStore.createValue("devices.volume.restore.boot.enabled", true)

    // TODO per device?
    val autoplayKeycode = dataStore.createValue("devices.speaker.autosave.enabled", KeyEvent.KEYCODE_MEDIA_PLAY)

    override val mapper = PreferenceStoreMapper(
        debugSettings.isDebugMode,
        themeMode,
        themeStyle,
    )

    companion object {
        internal val TAG = logTag("Devices", "Settings")
    }
}