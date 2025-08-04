package eu.darken.bluemusic.main.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.datastore.PreferenceData
import eu.darken.bluemusic.common.datastore.createValue
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.theming.ThemeMode
import eu.darken.bluemusic.common.theming.ThemeStyle
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeneralSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
    json: Json,
) : PreferenceData {

    private val Context.dataStore by preferencesDataStore(name = "settings_core")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val themeMode = dataStore.createValue("core.ui.theme.mode", ThemeMode.SYSTEM, json)
    val themeStyle = dataStore.createValue("core.ui.theme.style", ThemeStyle.DEFAULT, json)

    val isOnboardingCompleted = dataStore.createValue("core.onboarding.completed", false)

    val isBatteryOptimizationHintDismissed = dataStore.createValue("hints.battery.optimization.dismissed", false)
    val isAndroid10AppLaunchHintDismissed = dataStore.createValue("hints.android10.applaunch.dismissed", false)
    val isNotificationPermissionHintDismissed = dataStore.createValue("hints.notification.permission.dismissed", false)

    val legacyDatabaseMigrationDone = dataStore.createValue("legancy.migration.database.done", false)
    val legacySettingsMigrationDone = dataStore.createValue("legancy.migration.settings.done", false)

    companion object {
        internal val TAG = logTag("Core", "Settings")
    }
}