package eu.darken.bluemusic.monitor.core.alert

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri

@Singleton
class AlertTool @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var currentRingtone: Ringtone? = null

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun playAlert(type: AlertType, soundUri: String? = null) {
        log(TAG) { "playAlert(type=$type, soundUri=$soundUri)" }

        when (type) {
            AlertType.NONE -> {}
            AlertType.SOUND -> playSound(soundUri)
            AlertType.VIBRATION -> vibrate()
            AlertType.BOTH -> {
                playSound(soundUri)
                vibrate()
            }
        }
    }

    private fun playSound(soundUri: String?) {
        try {
            currentRingtone?.stop()

            val uri = if (soundUri.isNullOrEmpty()) {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            } else {
                soundUri.toUri()
            }

            var ringtone = RingtoneManager.getRingtone(context, uri)
            if (ringtone == null && !soundUri.isNullOrEmpty()) {
                log(TAG) { "Failed to get ringtone for uri: $uri, falling back to default" }
                val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ringtone = RingtoneManager.getRingtone(context, defaultUri)
            }
            if (ringtone == null) {
                log(TAG) { "Failed to get any ringtone" }
                return
            }
            currentRingtone = ringtone
            ringtone.play()
            log(TAG) { "Playing sound: $uri" }
        } catch (e: Exception) {
            log(TAG) { "Failed to play sound: ${e.message}" }
        }
    }

    private fun vibrate() {
        try {
            if (!vibrator.hasVibrator()) {
                log(TAG) { "Device does not have vibrator hardware" }
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(VIBRATION_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(VIBRATION_DURATION_MS)
            }
            log(TAG) { "Vibrating for $VIBRATION_DURATION_MS ms" }
        } catch (e: Exception) {
            log(TAG) { "Failed to vibrate: ${e.message}" }
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "Alert", "Tool")
        private const val VIBRATION_DURATION_MS = 500L
    }
}
