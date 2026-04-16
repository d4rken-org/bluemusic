package eu.darken.bluemusic.main.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.BuildConfigWrap
import eu.darken.bluemusic.common.datastore.PreferenceData
import eu.darken.bluemusic.common.datastore.createValue
import eu.darken.bluemusic.common.datastore.value
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.main.backup.core.GeneralSettingsBackup
import eu.darken.bluemusic.common.theming.ThemeColor
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

    val themeMode = dataStore.createValue(
        "core.ui.theme.mode", ThemeMode.SYSTEM, json,
        onErrorFallbackToDefault = BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.RELEASE,
    )
    val themeStyle = dataStore.createValue(
        "core.ui.theme.style", ThemeStyle.DEFAULT, json,
        onErrorFallbackToDefault = BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.RELEASE,
    )
    val themeColor = dataStore.createValue(
        "core.ui.theme.color", ThemeColor.BLUE, json,
        onErrorFallbackToDefault = BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.RELEASE,
    )

    val isOnboardingCompleted = dataStore.createValue("core.onboarding.completed", false)

    val isBatteryOptimizationHintDismissed = dataStore.createValue("hints.battery.optimization.dismissed", false)
    val isAndroid10AppLaunchHintDismissed = dataStore.createValue("hints.android10.applaunch.dismissed", false)
    val isNotificationPermissionHintDismissed = dataStore.createValue("hints.notification.permission.dismissed", false)


    suspend fun toBackup(): GeneralSettingsBackup = GeneralSettingsBackup(
        themeMode = themeMode.value().name,
        themeStyle = themeStyle.value().name,
        themeColor = themeColor.value().name,
        isOnboardingCompleted = isOnboardingCompleted.value(),
        isBatteryOptimizationHintDismissed = isBatteryOptimizationHintDismissed.value(),
        isAndroid10AppLaunchHintDismissed = isAndroid10AppLaunchHintDismissed.value(),
        isNotificationPermissionHintDismissed = isNotificationPermissionHintDismissed.value(),
    )

    suspend fun applyBackup(backup: GeneralSettingsBackup) {
        themeMode.value(ThemeMode.entries.find { it.name == backup.themeMode } ?: ThemeMode.SYSTEM)
        themeStyle.value(ThemeStyle.entries.find { it.name == backup.themeStyle } ?: ThemeStyle.DEFAULT)
        themeColor.value(ThemeColor.entries.find { it.name == backup.themeColor } ?: ThemeColor.BLUE)
        // Progression flags: only set to true, never revert to false
        if (backup.isOnboardingCompleted) isOnboardingCompleted.value(true)
        if (backup.isBatteryOptimizationHintDismissed) isBatteryOptimizationHintDismissed.value(true)
        if (backup.isAndroid10AppLaunchHintDismissed) isAndroid10AppLaunchHintDismissed.value(true)
        if (backup.isNotificationPermissionHintDismissed) isNotificationPermissionHintDismissed.value(true)
    }

    fun detectUnknownEnums(backup: GeneralSettingsBackup): List<String> = buildList {
        if (ThemeMode.entries.none { it.name == backup.themeMode }) {
            add("Unknown theme mode '${backup.themeMode}', defaulting to System")
        }
        if (ThemeStyle.entries.none { it.name == backup.themeStyle }) {
            add("Unknown theme style '${backup.themeStyle}', defaulting to Default")
        }
        if (ThemeColor.entries.none { it.name == backup.themeColor }) {
            add("Unknown theme color '${backup.themeColor}', defaulting to Blue")
        }
    }

    companion object {
        internal val TAG = logTag("Core", "Settings")
    }
}