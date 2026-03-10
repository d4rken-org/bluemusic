package testhelpers.coroutine

import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class TestDispatcherProvider(private val dispatcher: CoroutineDispatcher? = null) : DispatcherProvider {
    override val Default: CoroutineDispatcher
        get() = dispatcher ?: Dispatchers.Unconfined
    override val Main: CoroutineDispatcher
        get() = dispatcher ?: Dispatchers.Unconfined
    override val MainImmediate: CoroutineDispatcher
        get() = dispatcher ?: Dispatchers.Unconfined
    override val Unconfined: CoroutineDispatcher
        get() = dispatcher ?: Dispatchers.Unconfined
    override val IO: CoroutineDispatcher
        get() = dispatcher ?: Dispatchers.Unconfined
}

fun CoroutineScope.asDispatcherProvider() = this.coroutineContext.asDispatcherProvider()

fun kotlin.coroutines.CoroutineContext.asDispatcherProvider(): TestDispatcherProvider {
    val dispatcher = this[CoroutineDispatcher] ?: Dispatchers.Unconfined
    return TestDispatcherProvider(dispatcher = dispatcher)
}
