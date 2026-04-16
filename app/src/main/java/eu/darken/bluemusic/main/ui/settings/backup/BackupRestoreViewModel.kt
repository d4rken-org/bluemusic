package eu.darken.bluemusic.main.ui.settings.backup

import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.devices.core.database.DeviceDatabase
import eu.darken.bluemusic.main.backup.core.AppBackup
import eu.darken.bluemusic.main.backup.core.BackupRestoreManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    navCtrl: NavigationController,
    private val backupRestoreManager: BackupRestoreManager,
    private val deviceDatabase: DeviceDatabase,
) : ViewModel4(dispatcherProvider, logTag("Settings", "Backup", "VM"), navCtrl) {

    private val eventChannel = Channel<BackupRestoreEvent>()
    val events = eventChannel.receiveAsFlow()

    private val isWorkingFlow = MutableStateFlow(false)
    private val skipExistingFlow = MutableStateFlow(false)
    private val pendingRestoreFlow = MutableStateFlow<RestorePreview?>(null)
    private val lastResultFlow = MutableStateFlow<OperationResult?>(null)

    val state = combine(
        isWorkingFlow,
        skipExistingFlow,
        pendingRestoreFlow,
        lastResultFlow,
    ) { isWorking, skipExisting, pendingRestore, lastResult ->
        State(
            isWorking = isWorking,
            skipExisting = skipExisting,
            pendingRestore = pendingRestore,
            lastResult = lastResult,
        )
    }.asStateFlow()

    fun onBackupClicked() = launch {
        log(tag, INFO) { "onBackupClicked()" }
        lastResultFlow.value = null
        pendingRestoreFlow.value = null
        val suggestedName = "bluemusic-backup-${LocalDate.now()}.zip"
        eventChannel.send(BackupRestoreEvent.OpenBackupPicker(suggestedName))
    }

    fun onRestoreClicked() = launch {
        log(tag, INFO) { "onRestoreClicked()" }
        lastResultFlow.value = null
        pendingRestoreFlow.value = null
        eventChannel.send(BackupRestoreEvent.OpenRestorePicker)
    }

    fun onBackupUriSelected(uri: Uri) = launch {
        log(tag, INFO) { "onBackupUriSelected(uri=$uri)" }
        isWorkingFlow.value = true
        try {
            val result = backupRestoreManager.createBackup(uri)
            val fileName = backupRestoreManager.resolveFileName(uri)
            lastResultFlow.value = OperationResult(
                type = OperationType.BACKUP,
                deviceCount = result.deviceConfigCount,
                skippedCount = 0,
                fileName = fileName,
            )
        } finally {
            isWorkingFlow.value = false
        }
    }

    fun onRestoreUriSelected(uri: Uri) = launch {
        log(tag, INFO) { "onRestoreUriSelected(uri=$uri)" }
        isWorkingFlow.value = true
        try {
            val parseResult = backupRestoreManager.parseBackup(uri)
            val fileName = backupRestoreManager.resolveFileName(uri)

            val existingAddresses = deviceDatabase.devices.getAllDevices().first()
                .map { it.address }
                .toSet()
            val overlapCount = parseResult.backup.deviceConfigs.count { it.address in existingAddresses }

            pendingRestoreFlow.value = RestorePreview(
                fileUri = uri,
                fileName = fileName,
                backup = parseResult.backup,
                versionMismatch = parseResult.versionMismatch,
                deviceCount = parseResult.backup.deviceConfigs.size,
                existingDeviceCount = overlapCount,
                warnings = parseResult.enumWarnings,
            )
        } finally {
            isWorkingFlow.value = false
        }
    }

    fun onConfirmRestore() = launch {
        val preview = pendingRestoreFlow.value ?: return@launch
        log(tag, INFO) { "onConfirmRestore()" }
        pendingRestoreFlow.value = null
        isWorkingFlow.value = true
        try {
            val result = backupRestoreManager.applyRestore(
                backup = preview.backup,
                skipExisting = skipExistingFlow.value,
            )
            lastResultFlow.value = OperationResult(
                type = OperationType.RESTORE,
                deviceCount = result.deviceCount,
                skippedCount = result.skippedCount,
                fileName = preview.fileName,
            )
        } finally {
            isWorkingFlow.value = false
        }
    }

    fun onCancelRestore() {
        log(tag, INFO) { "onCancelRestore()" }
        pendingRestoreFlow.value = null
    }

    fun onToggleSkipExisting(skip: Boolean) {
        log(tag) { "onToggleSkipExisting($skip)" }
        skipExistingFlow.value = skip
    }

    data class State(
        val isWorking: Boolean = false,
        val skipExisting: Boolean = false,
        val pendingRestore: RestorePreview? = null,
        val lastResult: OperationResult? = null,
    )

    data class RestorePreview(
        val fileUri: Uri,
        val fileName: String,
        val backup: AppBackup,
        val versionMismatch: Boolean,
        val deviceCount: Int,
        val existingDeviceCount: Int,
        val warnings: List<String>,
    )

    enum class OperationType { BACKUP, RESTORE }

    data class OperationResult(
        val type: OperationType,
        val deviceCount: Int,
        val skippedCount: Int,
        val fileName: String,
    )
}
