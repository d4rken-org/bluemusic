package eu.darken.bluemusic.monitor.core.audio

import android.app.NotificationManager
import android.os.Build
import androidx.annotation.RequiresApi
import eu.darken.bluemusic.common.hasApiLevel

enum class DndMode(val key: String) {
    OFF("off"),
    PRIORITY_ONLY("priority_only"),
    ALARMS_ONLY("alarms_only"),
    TOTAL_SILENCE("total_silence");

    @RequiresApi(Build.VERSION_CODES.M)
    fun toInterruptionFilter(): Int = when (this) {
        OFF -> NotificationManager.INTERRUPTION_FILTER_ALL
        PRIORITY_ONLY -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
        ALARMS_ONLY -> NotificationManager.INTERRUPTION_FILTER_ALARMS
        TOTAL_SILENCE -> NotificationManager.INTERRUPTION_FILTER_NONE
    }

    companion object {
        @RequiresApi(Build.VERSION_CODES.M)
        fun fromInterruptionFilter(filter: Int): DndMode = when (filter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> OFF
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> PRIORITY_ONLY
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> ALARMS_ONLY
            NotificationManager.INTERRUPTION_FILTER_NONE -> TOTAL_SILENCE
            else -> OFF
        }

        fun fromKey(key: String?): DndMode? = when (key) {
            null -> null
            "off" -> OFF
            "priority_only" -> PRIORITY_ONLY
            "alarms_only" -> ALARMS_ONLY
            "total_silence" -> TOTAL_SILENCE
            else -> null
        }

        /**
         * Apps targeting Android 15+ (API 35) can no longer turn DND off via
         * [NotificationManager.setInterruptionFilter]. Legacy calls now toggle an app-owned
         * implicit AutomaticZenRule instead of clearing global DND, so requesting [OFF] can't
         * reliably turn DND off and may even surface as DND turning on. See discussion #230.
         *
         * Gated on the runtime SDK because devices below API 35 keep the legacy behavior
         * regardless of the app's target.
         */
        fun canTurnDndOff(): Boolean = !hasApiLevel(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    }
}
