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
class RingerModeHelper @Inject constructor(
    private val audioManager: AudioManager
) {

    fun getCurrentRingerMode(): Int {
        return audioManager.ringerMode
    }

    private fun setRingerMode(mode: Int): Boolean {
        log(TAG, VERBOSE) { "setRingerMode(mode=$mode)" }

        val currentMode = getCurrentRingerMode()
        if (currentMode == mode) {
            log(TAG, VERBOSE) { "Ringer mode already set to $mode" }
            return false
        }

        return try {
            audioManager.ringerMode = mode
            log(TAG, DEBUG) { "Changed ringer mode from $currentMode to $mode" }
            true
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to set ringer mode: ${e.message}" }
            false
        }
    }

    fun setSilentMode(): Boolean {
        return setRingerMode(AudioManager.RINGER_MODE_SILENT)
    }

    fun setVibrateMode(): Boolean {
        return setRingerMode(AudioManager.RINGER_MODE_VIBRATE)
    }

    fun setNormalMode(): Boolean {
        return setRingerMode(AudioManager.RINGER_MODE_NORMAL)
    }

    companion object {
        private val TAG = logTag("Audio", "RingerModeHelper")
    }
}