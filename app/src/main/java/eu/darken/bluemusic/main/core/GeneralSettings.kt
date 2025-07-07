package eu.darken.bluemusic.main.core

import android.content.Context
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
class GeneralSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
    debugSettings: DebugSettings,
    json: Json,
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "settings_core")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val themeMode = dataStore.createValue("core.ui.theme.mode", ThemeMode.SYSTEM, json)
    val themeStyle = dataStore.createValue("core.ui.theme.style", ThemeStyle.DEFAULT, json)

    val usePreviews = dataStore.createValue("core.ui.previews.enabled", true)

    val isOnboardingCompleted = dataStore.createValue("core.onboarding.completed", false)

    override val mapper = PreferenceStoreMapper(
        debugSettings.isDebugMode,
        themeMode,
        themeStyle,
        usePreviews,
    )

    companion object {
        internal val TAG = logTag("Core", "Settings")
    }
}