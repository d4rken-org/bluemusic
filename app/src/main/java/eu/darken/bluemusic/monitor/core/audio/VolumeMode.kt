package eu.darken.bluemusic.monitor.core.audio

import androidx.compose.runtime.Immutable

@Immutable
sealed class VolumeMode {
    data class Normal(val percentage: Float) : VolumeMode() {
        init {
            require(percentage in 0f..1f) { "Volume percentage must be between 0 and 1" }
        }
    }

    data object Silent : VolumeMode()
    data object Vibrate : VolumeMode()

    companion object {
        const val LEGACY_SILENT_VALUE = -2f
        const val LEGACY_VIBRATE_VALUE = -3f

        fun fromFloat(value: Float?): VolumeMode? = when {
            value == null -> null
            value == LEGACY_SILENT_VALUE -> Silent
            value == LEGACY_VIBRATE_VALUE -> Vibrate
            value in 0f..1f -> Normal(value)
            else -> null
        }

        fun VolumeMode?.toFloat(): Float? = when (this) {
            null -> null
            is Normal -> percentage
            Silent -> LEGACY_SILENT_VALUE
            Vibrate -> LEGACY_VIBRATE_VALUE
        }
    }
}