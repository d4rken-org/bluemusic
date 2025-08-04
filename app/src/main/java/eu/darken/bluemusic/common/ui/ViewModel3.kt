package eu.darken.bluemusic.common.ui

import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.error.ErrorEventSource
import eu.darken.bluemusic.common.flow.SingleEventFlow
import kotlinx.coroutines.CoroutineExceptionHandler

abstract class ViewModel3(
    dispatcherProvider: DispatcherProvider,
    override val tag: String = defaultTag(),
) : ViewModel2(dispatcherProvider), ErrorEventSource {

    override val errorEvents = SingleEventFlow<Throwable>()

    override var launchErrorHandler: CoroutineExceptionHandler? = CoroutineExceptionHandler { _, ex ->
        log(tag) { "Error during launch: ${ex.asLog()}" }
        errorEvents.emitBlocking(ex)
    }

    companion object {
        private fun defaultTag(): String = this::class.simpleName ?: "VM3"
    }
}
