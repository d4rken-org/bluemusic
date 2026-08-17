package eu.darken.bluemusic.eqspike.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.ui.ViewModel3
import eu.darken.bluemusic.eqspike.core.EqSpikeRepo
import javax.inject.Inject

@HiltViewModel
class EqSpikeViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val repo: EqSpikeRepo,
) : ViewModel3(dispatcherProvider, logTag("EqSpike", "VM")) {

    val state = repo.state

    fun startListening() = launch {
        log(tag) { "startListening()" }
        repo.startListening()
    }

    fun stopListening() = launch {
        log(tag) { "stopListening()" }
        repo.stopListening()
    }

    fun attach(packageName: String, sessionId: Int) = launch {
        log(tag) { "attach($packageName, $sessionId)" }
        repo.attach(packageName, sessionId)
    }

    fun detach(packageName: String, sessionId: Int) = launch {
        log(tag) { "detach($packageName, $sessionId)" }
        repo.detach(packageName, sessionId)
    }

    fun clear() = launch {
        log(tag) { "clear()" }
        repo.clear()
    }
}
