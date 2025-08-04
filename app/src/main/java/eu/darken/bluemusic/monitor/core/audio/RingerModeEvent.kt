package eu.darken.bluemusic.monitor.core.audio

data class RingerModeEvent(
    val oldMode: RingerMode,
    val newMode: RingerMode,
)