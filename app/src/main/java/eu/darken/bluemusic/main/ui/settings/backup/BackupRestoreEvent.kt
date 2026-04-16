package eu.darken.bluemusic.main.ui.settings.backup

sealed interface BackupRestoreEvent {
    data class OpenBackupPicker(val suggestedName: String) : BackupRestoreEvent
    data object OpenRestorePicker : BackupRestoreEvent
}
