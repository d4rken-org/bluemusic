package eu.darken.bluemusic.monitor.core.audio

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.os.Build
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.hasApiLevel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DndTool @Inject constructor(
    private val notificationManager: NotificationManager,
) {
    @SuppressLint("NewApi")
    fun setDndMode(mode: DndMode): Boolean {
        if (!hasApiLevel(Build.VERSION_CODES.M)) {
            log(TAG, WARN) { "DND mode requires API 23+" }
            return false
        }

        if (!hasNotificationPolicyAccess()) {
            log(TAG, WARN) { "No notification policy access for DND mode change" }
            return false
        }

        val rawFilter = notificationManager.currentInterruptionFilter
        val currentMode = DndMode.fromInterruptionFilter(rawFilter)
        log(TAG, VERBOSE) { "setDndMode(mode=$mode, currentMode=$currentMode, rawFilter=$rawFilter, sdk=${Build.VERSION.SDK_INT})" }

        if (mode == DndMode.OFF && !DndMode.canTurnDndOff()) {
            log(TAG, WARN) { "Ignoring DndMode.OFF: apps can't turn DND off on API ${Build.VERSION.SDK_INT} (discussion #230). currentMode=$currentMode rawFilter=$rawFilter" }
            return false
        }

        if (currentMode == mode) {
            log(TAG, VERBOSE) { "DND mode already set to $mode (rawFilter=$rawFilter)" }
            return false
        }

        return try {
            notificationManager.setInterruptionFilter(mode.toInterruptionFilter())
            log(TAG, DEBUG) { "Changed DND mode from $currentMode to $mode (rawFilter was $rawFilter)" }
            true
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to set DND mode: ${e.message}" }
            false
        }
    }

    @SuppressLint("NewApi")
    fun hasNotificationPolicyAccess(): Boolean = if (hasApiLevel(Build.VERSION_CODES.M)) {
        notificationManager.isNotificationPolicyAccessGranted
    } else {
        true // Prior to Android M, notification policy access doesn't exist
    }

    companion object {
        private val TAG = logTag("Audio", "DndTool")
    }
}