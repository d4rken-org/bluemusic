package eu.darken.bluemusic.monitor.core.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.time.delay
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt


fun levelToPercentage(current: Int, min: Int, max: Int): Float {
    val range = max - min
    if (range <= 0) return 0f
    return ((current - min).toFloat() / range).coerceIn(0f, 1f)
}

fun percentageToLevel(percentage: Float, min: Int, max: Int): Int {
    return (min + (max - min) * percentage).roundToInt()
}

@Singleton
class VolumeTool @Inject constructor(
    private val audioManager: AudioManager,
) {

    internal var clock: () -> Long = SystemClock::elapsedRealtime

    private val lock = Mutex()
    private val writeTracker = VolumeWriteTracker(clock = { clock() })

    fun getCurrentVolume(id: AudioStream.Id): Int {
        return audioManager.getStreamVolume(id.id)
    }

    fun getMinVolume(streamId: AudioStream.Id): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return 0
        return try {
            audioManager.getStreamMinVolume(streamId.id)
        } catch (_: IllegalArgumentException) {
            // STREAM_BLUETOOTH_HANDSFREE (type 6) is not a public stream type,
            // so getStreamMinVolume rejects it. It shares the same audio path as
            // STREAM_VOICE_CALL, so use that stream's min as a proxy.
            if (streamId == AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE) {
                try {
                    audioManager.getStreamMinVolume(AudioStream.Id.STREAM_VOICE_CALL.id)
                } catch (_: IllegalArgumentException) {
                    0
                }
            } else {
                0
            }
        }
    }

    fun getMaxVolume(streamId: AudioStream.Id): Int {
        return audioManager.getStreamMaxVolume(streamId.id)
    }

    private suspend fun setVolume(streamId: AudioStream.Id, volume: Int, flags: Int) = lock.withLock {
        log(TAG, VERBOSE) { "setVolume(streamId=$streamId, volume=$volume, flags=$flags)." }
        try {
            writeTracker.onWriteStarted(streamId, volume)

            delay(10)

            // https://stackoverflow.com/questions/6733163/notificationmanager-notify-fails-with-securityexception
            try {
                audioManager.setStreamVolume(streamId.id, volume, flags)
            } catch (e: SecurityException) {
                log(TAG, WARN) { "setStreamVolume($streamId, $volume) denied: ${e.message}" }
            }

            delay(10)
        } finally {
            writeTracker.onWriteFinished()
        }
    }

    internal fun hasRecentTarget(id: AudioStream.Id, volume: Int): Boolean {
        return writeTracker.hasRecentTarget(id, volume)
    }

    fun wasUs(id: AudioStream.Id, volume: Int): Boolean {
        return writeTracker.wasUs(id, volume)
    }

    fun getVolumePercentage(streamId: AudioStream.Id): Float {
        return levelToPercentage(getCurrentVolume(streamId), getMinVolume(streamId), getMaxVolume(streamId))
    }

    /**
     * Diagnostic only (issue #232): describes the active media output route.
     * On some Android 16 builds the audio route tears down ~2s before
     * ACL_DISCONNECTED, so the phone speaker's volume gets attributed to the
     * disconnecting BT device. Logging this alongside volume changes lets a
     * debug log reveal whether media is still routed to Bluetooth at that moment.
     *
     * API 33+ returns the predicted active route; below that only the connected
     * output list is available (labelled accordingly, not the active route).
     */
    @Suppress("DEPRECATION") // isBluetoothA2dpOn/ScoOn are deprecated but still the routing signal we want to log
    fun describeActiveMediaRoute(): String {
        val start = clock()
        return try {
            val a2dp = audioManager.isBluetoothA2dpOn
            val sco = audioManager.isBluetoothScoOn
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val devices = audioManager.getAudioDevicesForAttributes(attrs)
                val desc = devices.joinToString(",") { describeDevice(it) }.ifEmpty { "none" }
                "predicted=[$desc] a2dpOn=$a2dp scoOn=$sco queryMs=${clock() - start}"
            } else {
                val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val desc = outputs.joinToString(",") { describeDevice(it) }.ifEmpty { "none" }
                "availableOnly=[$desc] a2dpOn=$a2dp scoOn=$sco queryMs=${clock() - start} (no active-route API < API33)"
            }
        } catch (e: Exception) {
            "route-query-failed: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private fun describeDevice(device: AudioDeviceInfo): String {
        val name = typeName(device.type)
        val product = device.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        return if (product != null) "$name#${device.type} '$product'" else "$name#${device.type}"
    }

    // AudioDeviceInfo.TYPE_* are compile-time int constants (inlined), safe to
    // reference regardless of minSdk. The raw type int is always printed too.
    private fun typeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BT_A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BT_SCO"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "SPEAKER"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "SPEAKER_SAFE"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "EARPIECE"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HP"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HS"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
        AudioDeviceInfo.TYPE_HEARING_AID -> "HEARING_AID"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE_HEADSET"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE_SPEAKER"
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> "BLE_BROADCAST"
        else -> "OTHER"
    }

    suspend fun lowerByOne(streamId: AudioStream.Id, visible: Boolean): Boolean {
        val current = getCurrentVolume(streamId)
        val min = getMinVolume(streamId)
        val max = getMaxVolume(streamId)
        log(TAG, VERBOSE) { "lowerByOne(streamId=$streamId, visible=$visible): current=$current, min=$min, max=$max" }

        if (current <= min) {
            log(TAG, WARN) { "Volume was at min ($min), can't lower by one more." }
            return false
        }

        return changeVolume(streamId, levelToPercentage(current - 1, min, max), visible)
    }

    suspend fun increaseByOne(streamId: AudioStream.Id, visible: Boolean): Boolean {
        val current = getCurrentVolume(streamId)
        val min = getMinVolume(streamId)
        val max = getMaxVolume(streamId)
        log(TAG, VERBOSE) { "increaseByOne(streamId=$streamId, visible=$visible): current=$current, min=$min, max=$max" }

        if (current >= max) {
            log(TAG, WARN) { "Volume was at max ($max), can't increase by one more." }
            return false
        }

        return changeVolume(streamId, levelToPercentage(current + 1, min, max), visible)
    }

    suspend fun changeVolume(
        streamId: AudioStream.Id,
        percent: Float,
        visible: Boolean = false,
        delay: Duration = Duration.ZERO,
    ): Boolean {
        log(TAG, VERBOSE) { "changeVolume(streamId=$streamId, percent=$percent, visible=$visible, delay=$delay)" }
        val target = percentageToLevel(percent, getMinVolume(streamId), getMaxVolume(streamId))
        return changeVolume(
            streamId = streamId,
            targetLevel = target,
            visible = visible,
            delay = delay
        )
    }

    suspend fun changeVolume(
        streamId: AudioStream.Id,
        targetLevel: Int,
        visible: Boolean = false,
        delay: Duration = Duration.ZERO,
    ): Boolean {
        log(TAG, VERBOSE) { "changeVolume(streamId=$streamId, level=$targetLevel, visible=$visible, delay=$delay)" }

        val max = getMaxVolume(streamId)
        val min = getMinVolume(streamId)
        if (min > max) {
            log(TAG, WARN) { "Invalid stream bounds: min=$min > max=$max for $streamId; aborting changeVolume." }
            return false
        }
        val clampedTarget = targetLevel.coerceIn(min, max)
        if (clampedTarget != targetLevel) {
            log(TAG, WARN) { "Target level $targetLevel clamped to $clampedTarget (min=$min, max=$max)." }
        }

        val currentLevel = getCurrentVolume(streamId)
        if (currentLevel == clampedTarget) {
            writeTracker.rememberCurrentTarget(streamId, clampedTarget)
            log(TAG, VERBOSE) { "Target volume of $clampedTarget already set." }
            return false
        }

        log(TAG, DEBUG) {
            "Adjusting volume (streamId=$streamId, targetLevel=$clampedTarget, current=$currentLevel, max=$max, visible=$visible, delay=$delay)."
        }
        val flag = if (visible) AudioManager.FLAG_SHOW_UI else 0
        if (delay == Duration.ZERO) {
            setVolume(streamId, clampedTarget, flag)
        } else {
            val range: IntProgression = if (currentLevel < clampedTarget) {
                (currentLevel + 1)..clampedTarget
            } else {
                (currentLevel - 1) downTo clampedTarget
            }
            for (step in range) {
                setVolume(streamId, step, flag)
                if (step != range.last) delay(delay)
            }
        }
        return true
    }

    companion object {
        private val TAG = logTag("Audio", "StreamHelper")
    }
}
