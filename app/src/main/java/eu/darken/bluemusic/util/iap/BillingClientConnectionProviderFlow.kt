package eu.darken.bluemusic.util.iap

import android.content.Context
import eu.darken.bluemusic.AppComponent
import eu.darken.bluemusic.common.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

@AppComponent.Scope
class BillingClientConnectionProviderFlow @Inject constructor(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)
    
    val connection: StateFlow<BillingClientConnectionFlow> = flow {
        Timber.d("Creating new BillingClientConnection")
        val connection = BillingClientConnectionFlow(context, dispatcherProvider)
        emit(connection)
    }
    .stateIn(
        scope = scope,
        started = SharingStarted.Lazily,
        initialValue = BillingClientConnectionFlow(context, dispatcherProvider)
    )
}