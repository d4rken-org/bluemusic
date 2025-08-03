package eu.darken.bluemusic.devices.ui.config

import android.content.Intent
import java.time.Duration

sealed interface ConfigEvent {
    data object ShowDeleteDialog : ConfigEvent
    data class ShowRenameDialog(val currentName: String) : ConfigEvent
    data class ShowMonitoringDurationDialog(val currentValue: Duration) : ConfigEvent
    data class ShowReactionDelayDialog(val currentValue: Duration) : ConfigEvent
    data class ShowAdjustmentDelayDialog(val currentValue: Duration) : ConfigEvent
    data class ShowVolumeRateLimitDialog(val currentValue: Duration) : ConfigEvent
    data object NavigateBack : ConfigEvent
    data object RequiresPro : ConfigEvent
    data class RequiresNotificationPolicyAccessForRingtone(val intent: Intent) : ConfigEvent
    data class RequiresNotificationPolicyAccessForNotification(val intent: Intent) : ConfigEvent
}