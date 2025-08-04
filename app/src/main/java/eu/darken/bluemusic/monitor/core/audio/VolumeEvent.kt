package eu.darken.bluemusic.monitor.core.audio

data class VolumeEvent(
    val streamId: AudioStream.Id,
    val oldVolume: Int,
    val newVolume: Int,
    val self: Boolean,
)