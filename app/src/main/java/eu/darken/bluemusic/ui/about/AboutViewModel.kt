package eu.darken.bluemusic.ui.about

import android.content.Context
import eu.darken.bluemusic.BuildConfig
import eu.darken.bluemusic.common.architecture.BaseViewModel
import eu.darken.bluemusic.common.coroutines.DispatcherProvider
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

data class AboutState(
    val version: String = ""
)

sealed interface AboutEvent

class AboutViewModel @Inject constructor(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider
) : BaseViewModel<AboutState, AboutEvent>(AboutState()) {
    
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
                Timber.e(e, "Failed to load version")
            }
        }
    }
    
    override fun onEvent(event: AboutEvent) {
        // No events to handle
    }
}