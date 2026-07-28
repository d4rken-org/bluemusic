package eu.darken.bluemusic.common.ui

import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.navigation.NavigationDestination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn


/**
 * Compose render state should be exposed as VM-owned [StateFlow]s. Those render-state flows must
 * stay collector-safe and never throw into `collectAsStateWithLifecycle()`. Use [safeStateIn] to
 * forward recoverable failures to `errorEvents` and emit an explicit fallback UI state instead.
 */
abstract class ViewModel4(
    dispatcherProvider: DispatcherProvider,
    override val tag: String = defaultTag(),
    private val navCtrl: NavigationController,
) : ViewModel3(dispatcherProvider, tag) {

    fun navTo(
        destination: NavigationDestination,
        popUpTo: NavigationDestination? = null,
        inclusive: Boolean = false
    ) {
        log(tag) { "goTo($destination)" }
        navCtrl.goTo(destination, popUpTo, inclusive)
    }

    fun navUp() {
        log(tag) { "navUp()" }
        navCtrl.up()
    }

    /**
     * Collect a render-state flow in [vmScope] and convert upstream failures into explicit fallback
     * UI state plus an `errorEvents` emission. Cancellation is never converted into UI state.
     */
    protected fun <T> Flow<T>.safeStateIn(
        initialValue: T,
        started: SharingStarted = SharingStarted.WhileSubscribed(5000),
        onError: (Throwable) -> T,
    ): StateFlow<T> = this
        .catch { ex ->
            if (ex is CancellationException) throw ex

            log(tag, WARN) { "Error during state collection: ${ex.asLog()}" }
            errorEvents.emit(ex)
            emit(onError(ex))
        }
        .stateIn(
            scope = vmScope,
            started = started,
            initialValue = initialValue,
        )

    companion object {
        private fun defaultTag(): String = this::class.simpleName ?: "VM3"
    }
}
