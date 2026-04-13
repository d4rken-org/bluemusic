package eu.darken.bluemusic.monitor.core.audio

import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolumeModeTool @Inject constructor(
    private val volumeTool: VolumeTool,
    private val ringerTool: RingerTool,
) {

    suspend fun alignSystemState(streamType: AudioStream.Type, volumeMode: VolumeMode): Boolean = when (volumeMode) {
        is VolumeMode.Normal -> {
            if (streamType == AudioStream.Type.RINGTONE && volumeMode.percentage > 0f) {
                ringerTool.setRingerMode(RingerMode.NORMAL)
            } else {
                false
            }
        }

        is VolumeMode.Silent -> {
            if (streamType == AudioStream.Type.RINGTONE) {
                ringerTool.setRingerMode(RingerMode.SILENT)
            } else {
                false
            }
        }

        is VolumeMode.Vibrate -> {
            if (streamType == AudioStream.Type.RINGTONE) {
                ringerTool.setRingerMode(RingerMode.VIBRATE)
            } else {
                false
            }
        }
    }

    fun snapToStep(streamId: AudioStream.Id, volumeMode: VolumeMode): VolumeMode = when (volumeMode) {
        is VolumeMode.Normal -> VolumeMode.Normal(volumeTool.snapPercentage(streamId, volumeMode.percentage))
        is VolumeMode.Silent,
        is VolumeMode.Vibrate -> volumeMode
    }

    suspend fun apply(
        streamId: AudioStream.Id,
        streamType: AudioStream.Type,
        volumeMode: VolumeMode,
        visible: Boolean,
        delay: Duration = Duration.ZERO,
    ): Boolean {
        alignSystemState(streamType, volumeMode)

        return when (volumeMode) {
            is VolumeMode.Normal -> volumeTool.changeVolume(
                streamId = streamId,
                percent = volumeMode.percentage,
                visible = visible,
                delay = delay,
            )

            is VolumeMode.Silent,
            is VolumeMode.Vibrate -> false
        }
    }
}
