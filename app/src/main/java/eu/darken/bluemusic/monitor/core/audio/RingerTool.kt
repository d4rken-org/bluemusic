package eu.darken.bluemusic.monitor.core.audio

import android.media.AudioManager
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RingerTool @Inject constructor(
    private val audioManager: AudioManager
) {

    fun getCurrentRingerMode(): RingerMode = when (audioManager.ringerMode) {
        AudioManager.RINGER_MODE_NORMAL -> RingerMode.NORMAL
        AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
        AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
        else -> RingerMode.NORMAL // Default fallback
    }

    fun setRingerMode(mode: RingerMode): Boolean {
        val androidMode = when (mode) {
            RingerMode.NORMAL -> AudioManager.RINGER_MODE_NORMAL
            RingerMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
            RingerMode.SILENT -> AudioManager.RINGER_MODE_SILENT
        }

        log(TAG, VERBOSE) { "setRingerMode(mode=$mode, androidMode=$androidMode)" }

        val currentMode = getCurrentRingerMode()
        if (currentMode == mode) {
            log(TAG, VERBOSE) { "Ringer mode already set to $mode" }
            return false
        }

        return try {
            audioManager.ringerMode = androidMode
            log(TAG, DEBUG) { "Changed ringer mode from $currentMode to $mode" }
            true
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to set ringer mode: ${e.message}" }
            false
        }
    }

    companion object {
        private val TAG = logTag("Audio", "RingerModeHelper")
    }
}