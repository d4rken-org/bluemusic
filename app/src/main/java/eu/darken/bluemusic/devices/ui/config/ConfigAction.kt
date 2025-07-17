package eu.darken.bluemusic.devices.ui.config

import eu.darken.bluemusic.monitor.core.audio.AudioStream
import java.time.Duration

sealed interface ConfigAction {
    data class OnToggleVolume(
        val type: AudioStream.Type,
    ) : ConfigAction

    data object OnToggleAutoPlay : ConfigAction
    data object OnToggleVolumeLock : ConfigAction
    data object OnToggleVolumeObserving : ConfigAction
    data object OnToggleVolumeRateLimiter : ConfigAction
    data object OnToggleKeepAwake : ConfigAction
    data object OnToggleNudgeVolume : ConfigAction
    data object OnLaunchAppClicked : ConfigAction
    data object OnClearLaunchApp : ConfigAction
    data object OnEditMonitoringDurationClicked : ConfigAction
    data object OnEditReactionDelayClicked : ConfigAction
    data object OnEditAdjustmentDelayClicked : ConfigAction
    data object OnEditVolumeRateLimitClicked : ConfigAction
    data object OnRenameClicked : ConfigAction
    data class DeleteDevice(val confirmed: Boolean = false) : ConfigAction
    data class OnEditMonitoringDuration(val duration: Duration?) : ConfigAction
    data class OnEditReactionDelay(val delay: Duration?) : ConfigAction
    data class OnEditAdjustmentDelay(val delay: Duration?) : ConfigAction
    data class OnEditVolumeRateLimit(val duration: Duration?) : ConfigAction
    data class OnRename(val newName: String) : ConfigAction
    data class OnConfirmDelete(val confirmed: Boolean) : ConfigAction
    data class OnAppSelected(val packageName: String) : ConfigAction
}