package eu.darken.bluemusic.common.debug.recorder.ui

import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.BlueMusicLinks
import eu.darken.bluemusic.common.BuildConfigWrap
import eu.darken.bluemusic.common.WebpageTool
import eu.darken.bluemusic.common.compression.Zipper
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.DynamicStateFlow
import eu.darken.bluemusic.common.flow.SingleEventFlow
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class RecorderViewModel @Inject constructor(
    navCtrl: NavigationController,
    dispatchers: DispatcherProvider,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatchers, logTag("Debug", "Recorder", "Screen", "VM"), navCtrl) {

    private val recordedPath: File

    private val stater: DynamicStateFlow<State>
    val state: Flow<State>

    val shareEvent = SingleEventFlow<Intent>()
    val finishEvent = SingleEventFlow<Unit>()

    private var compressionJob: Job? = null

    init {
        val path = savedStateHandle.get<String>(RecorderActivity.RECORD_PATH)
            ?: throw IllegalStateException("No path provided")
        recordedPath = File(path)

        stater = DynamicStateFlow(TAG, vmScope) {
            State(logDir = recordedPath)
        }
        state = stater.flow

        compressionJob = vmScope.launch {
            log(TAG) { "Getting log files in dir: $recordedPath" }
            val logFiles = recordedPath.listFiles() ?: emptyArray()
            log(TAG) { "Found ${logFiles.size} logfiles: $logFiles" }
            var entries = logFiles.map { LogFileItem(path = it) }
            stater.updateBlocking { copy(logEntries = entries) }

            log(TAG) { "Determining log file size..." }
            entries = entries.map { entry -> entry.copy(size = entry.path.length()) }.sortedByDescending { it.size }
            stater.updateBlocking { copy(logEntries = entries) }

            log(TAG) { "Compressing log files..." }
            val zipFile = File(recordedPath.parentFile, "${recordedPath.name}.zip")
            log(TAG) { "Writing zip file to $zipFile" }
            Zipper().zip(
                entries.map { it.path.path },
                zipFile.path
            )
            val zippedSize = zipFile.length()
            log(TAG) { "Zip file created ${zippedSize}B at $zipFile" }
            stater.updateBlocking {
                copy(compressedFile = zipFile, compressedSize = zippedSize, operationState = OperationState.READY)
            }
        }
    }

    fun share() = launch {
        val file = stater.value().compressedFile ?: throw IllegalStateException("compressedFile is null")

        val intent = Intent(Intent.ACTION_SEND).apply {
            val uri = FileProvider.getUriForFile(
                context,
                BuildConfigWrap.APPLICATION_ID + ".provider",
                file
            )

            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            type = "application/zip"

            addCategory(Intent.CATEGORY_DEFAULT)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "${BuildConfigWrap.APPLICATION_ID} DebugLog - ${BuildConfigWrap.VERSION_DESCRIPTION})"
            )
            putExtra(Intent.EXTRA_TEXT, "Your text here.")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooserIntent = Intent.createChooser(intent, context.getString(R.string.debug_log_file_label))
        shareEvent.emit(chooserIntent)
    }

    fun keep() {
        log(TAG) { "Keeping debug log files at $recordedPath" }
        finishEvent.tryEmit(Unit)
    }

    fun discard() = launch {
        log(TAG) { "Discarding debug log files at $recordedPath" }
        stater.updateBlocking { copy(operationState = OperationState.DELETING) }

        compressionJob?.cancelAndJoin()

        val zipFile = File(recordedPath.parentFile, "${recordedPath.name}.zip")
        if (zipFile.exists()) {
            log(TAG) { "Deleting zip file: $zipFile" }
            zipFile.delete()
        }
        if (recordedPath.exists()) {
            log(TAG) { "Deleting log directory: $recordedPath" }
            recordedPath.deleteRecursively()
        }

        finishEvent.emit(Unit)
    }

    fun goPrivacyPolicy() {
        webpageTool.open(BlueMusicLinks.PRIVACY_POLICY)
    }

    enum class OperationState {
        COMPRESSING,
        READY,
        DELETING,
    }

    data class State(
        val logDir: File,
        val logEntries: List<LogFileItem> = emptyList(),
        val compressedFile: File? = null,
        val compressedSize: Long? = null,
        val operationState: OperationState = OperationState.COMPRESSING,
    ) {
        val loading: Boolean
            get() = compressedSize == null

        fun getFormattedCompressedSize(context: Context): String? {
            return compressedSize?.let { Formatter.formatShortFileSize(context, it) }
        }
    }

    data class LogFileItem(
        val path: File,
        val size: Long? = null,
    ) {
        fun getFormattedSize(context: Context): String? {
            return size?.let { Formatter.formatShortFileSize(context, it) }
        }
    }

    companion object {
        internal val TAG = logTag("Debug", "Recorder", "ViewModel")
    }
}
