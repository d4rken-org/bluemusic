package eu.darken.bluemusic.main.ui.settings.about

import android.content.Context
import eu.darken.bluemusic.BuildConfig
import eu.darken.bluemusic.common.architecture.BaseViewModel
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AboutState(
    val version: String = ""
)

sealed interface AboutEvent

class AboutViewModel @Inject constructor(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider
) : BaseViewModel<AboutState, AboutEvent>(AboutState()) {

    companion object {
        private val TAG = logTag("AboutViewModel")
    }
    
    init {
        loadVersion()
    }
    
    private fun loadVersion() {
        launch {
            try {
                val version = withContext(dispatcherProvider.IO) {
                    val info = context.packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, 0)
                    "Version ${info.versionName} (${info.versionCode})"
                }
                updateState { copy(version = version) }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to load version: ${e.asLog()}" }
            }
        }
    }
    
    override fun onEvent(event: AboutEvent) {
        // No events to handle
    }
}
