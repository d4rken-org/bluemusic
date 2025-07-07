package eu.darken.bluemusic.common.ui

import androidx.lifecycle.viewModelScope
import eu.darken.bluemusic.common.coroutine.DefaultDispatcherProvider
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.*
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.flow.setupCommonEventHandlers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlin.coroutines.CoroutineContext


abstract class ViewModel2(
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    override val tag: String = defaultTag(),
) : ViewModel1(tag = tag) {

    abstract var launchErrorHandler: CoroutineExceptionHandler?

    private val vmContext by lazy {
        val dispatcher = dispatcherProvider.Default
        launchErrorHandler?.let { dispatcher + it } ?: dispatcher
    }

    val vmScope: CoroutineScope by lazy {
        viewModelScope + vmContext
    }

    fun launch(
        scope: CoroutineScope = viewModelScope,
        context: CoroutineContext = vmContext,
        block: suspend CoroutineScope.() -> Unit
    ) {
        try {
            scope.launch(context = context, block = block)
        } catch (e: CancellationException) {
            log(tag, WARN) { "launch()ed coroutine was canceled (scope=$scope): ${e.asLog()}" }
        }
    }

    open fun <T> Flow<T>.launchInViewModel() = this
        .setupCommonEventHandlers(tag) { "launchInViewModel()" }
        .launchIn(vmScope)

    fun <T> Flow<T>.asStateFlow(defaultValue: T? = null): Flow<T> = stateIn(
        vmScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = defaultValue,
    ).mapNotNull { it }

    companion object {
        private fun defaultTag(): String = this::class.simpleName ?: "VM2"
    }
}