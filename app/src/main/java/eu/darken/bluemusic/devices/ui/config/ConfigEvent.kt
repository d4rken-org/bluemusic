package eu.darken.bluemusic.devices.ui.config

sealed interface ConfigEvent {
    data object ShowPurchaseSnackbar : ConfigEvent
    data object ShowDeleteDialog : ConfigEvent
    data object ShowAppPickerDialog : ConfigEvent
    data class ShowRenameDialog(val currentName: String) : ConfigEvent
    data class ShowMonitoringDurationDialog(val currentValue: Long) : ConfigEvent
    data class ShowReactionDelayDialog(val currentValue: Long) : ConfigEvent
    data class ShowAdjustmentDelayDialog(val currentValue: Long) : ConfigEvent
    data object NavigateBack : ConfigEvent
}