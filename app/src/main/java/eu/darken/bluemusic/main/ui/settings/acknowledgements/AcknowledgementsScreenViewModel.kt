package eu.darken.bluemusic.main.ui.settings.acknowledgements

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.WebpageTool
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@HiltViewModel
class AcknowledgementsScreenViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    navCtrl: NavigationController,
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatcherProvider, logTag("Settings", "Acknowledgements", "Screen", "VM"), navCtrl) {

    val state = combine(
        flowOf(Unit)
    ) { _ ->
        State()
    }.asStateFlow()

    fun openUrl(url: String) = launch {
        log(tag) { "Opening URL: $url" }
        webpageTool.open(url)
    }

    data class State(
        val translators: Boolean = false
    )
}
