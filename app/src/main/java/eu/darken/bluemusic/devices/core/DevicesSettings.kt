package eu.darken.bluemusic.devices.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.BuildConfigWrap
import eu.darken.bluemusic.common.datastore.PreferenceData
import eu.darken.bluemusic.common.datastore.createValue
import eu.darken.bluemusic.common.debug.logging.logTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    /**
     * Read-only flag for whether monitoring is enabled. Use [setEnabled] for writes so the paired
     * toggle epoch is advanced in the same DataStore transaction.
     */
    val isEnabled = dataStore.createValue("devices.enabled", true)

    /**
     * Atomic snapshot of monitoring enablement plus a monotonic epoch that advances on every
     * actual toggle. This lets downstream consumers detect a collapsed `true -> false -> true`
     * cycle even if they only observe the final `true` value.
     */
    val enabledState: Flow<EnabledState> = dataStore.data
        .map { prefs ->
            EnabledState(
                isEnabled = prefs[KEY_ENABLED] ?: true,
                toggleEpoch = prefs[KEY_ENABLED_EPOCH] ?: 0L,
            )
        }
        .distinctUntilChanged()

    val restoreOnBoot = dataStore.createValue("devices.volume.restore.boot.enabled", true)
    val lockedDevices = dataStore.createValue(
        "devices.adjustment.locked", emptySet<String>(), json,
        onErrorFallbackToDefault = BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.RELEASE,
    )

    suspend fun currentEnabledState(): EnabledState = enabledState.first()

    /**
     * Updates the enabled flag and paired epoch atomically. The epoch increments only when the
     * boolean actually changes.
     */
    suspend fun setEnabled(enabled: Boolean): EnabledState {
        var updatedState = EnabledState(isEnabled = enabled, toggleEpoch = 0L)
        dataStore.updateData { prefs ->
            val currentState = EnabledState(
                isEnabled = prefs[KEY_ENABLED] ?: true,
                toggleEpoch = prefs[KEY_ENABLED_EPOCH] ?: 0L,
            )
            updatedState = if (currentState.isEnabled == enabled) {
                currentState
            } else {
                currentState.copy(isEnabled = enabled, toggleEpoch = currentState.toggleEpoch + 1L)
            }

            prefs.toMutablePreferences().apply {
                this[KEY_ENABLED] = updatedState.isEnabled
                this[KEY_ENABLED_EPOCH] = updatedState.toggleEpoch
            }.toPreferences()
        }
        return updatedState
    }

    data class EnabledState(
        val isEnabled: Boolean,
        val toggleEpoch: Long,
    )

    companion object {
        internal val TAG = logTag("Devices", "Settings")
        private val KEY_ENABLED = booleanPreferencesKey("devices.enabled")
        private val KEY_ENABLED_EPOCH = longPreferencesKey("devices.enabled.epoch")
    }
}
