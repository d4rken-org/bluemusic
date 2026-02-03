package eu.darken.bluemusic.monitor.core.alert

enum class AlertType(val key: String) {
    NONE("none"),
    SOUND("sound"),
    VIBRATION("vibration"),
    BOTH("both");

    companion object {
        fun fromKey(key: String?): AlertType = when (key) {
            null, "none" -> NONE
            "sound" -> SOUND
            "vibration" -> VIBRATION
            "both" -> BOTH
            else -> NONE
        }
    }
}
