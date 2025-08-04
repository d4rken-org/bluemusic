package eu.darken.bluemusic.main.core

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class LegacySettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val preferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    init {
        if (!preferences.contains(PREFKEY_INSTALLTIME)) {
            preferences.edit { putLong(PREFKEY_INSTALLTIME, System.currentTimeMillis()) }
        }
        var currentLaunchCount = preferences.getInt(PREFKEY_LAUNCHCOUNT, 0)
        preferences.edit { putInt(PREFKEY_LAUNCHCOUNT, ++currentLaunchCount) }
    }

    fun isVolumeAdjustedVisibly(): Boolean {
        return preferences!!.getBoolean(PREFKEY_VISIBLE_ADJUSTMENTS, true)
    }

    fun isVolumeChangeListenerEnabled(): Boolean {
        return preferences!!.getBoolean(PREFKEY_VOLUMELISTENER, false)
    }

    fun isEnabled(): Boolean {
        return preferences!!.getBoolean(PREFKEY_CORE_ENABLED, true)
    }

    fun isSpeakerAutoSaveEnabled(): Boolean {
        return preferences!!.getBoolean(PREFKEY_SPEAKER_AUTOSAVE, false)
    }

    fun getLaunchCount(): Int {
        return preferences!!.getInt(PREFKEY_LAUNCHCOUNT, 0)
    }

    fun getInstallTime(): Long {
        return preferences!!.getLong(PREFKEY_INSTALLTIME, System.currentTimeMillis())
    }

    fun isBootRestoreEnabled(): Boolean {
        return preferences!!.getBoolean(PREFKEY_BOOT_RESTORE, true)
    }

    companion object {
        const val PREFKEY_VOLUMELISTENER: String = "core.volume.changelistener"
        const val PREFKEY_VISIBLE_ADJUSTMENTS: String = "core.volume.visibleadjustments"
        const val PREFKEY_SPEAKER_AUTOSAVE: String = "core.speaker.autosave"

        const val PREFKEY_INSTALLTIME: String = "core.metrics.installtime"
        const val PREFKEY_LAUNCHCOUNT: String = "core.metrics.launchcount"
        const val PREFKEY_BOOT_RESTORE: String = "core.onboot.restore"
        const val PREFKEY_CORE_ENABLED: String = "core.enabled"
    }
}