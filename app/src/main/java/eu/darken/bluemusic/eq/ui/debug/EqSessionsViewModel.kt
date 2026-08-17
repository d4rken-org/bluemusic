package eu.darken.bluemusic.eq.ui.debug

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.ui.ViewModel3
import eu.darken.bluemusic.eq.core.EqCoordinator
import javax.inject.Inject

@HiltViewModel
class EqSessionsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val coordinator: EqCoordinator,
) : ViewModel3(dispatcherProvider, logTag("Eq", "Sessions", "VM")) {

    val state = coordinator.sessionState

    fun setListening(enabled: Boolean) = launch {
        log(tag) { "setListening($enabled)" }
        coordinator.setListening(enabled)
    }

    fun clear() = launch {
        log(tag) { "clear()" }
        coordinator.clearDiagnostics()
    }
}
