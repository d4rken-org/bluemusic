package eu.darken.bluemusic.common.coroutines

import dagger.Binds
import dagger.Module
import eu.darken.bluemusic.AppComponent

@Module
abstract class CoroutineModule {
    
    @Binds
    @AppComponent.Scope
    abstract fun bindDispatcherProvider(provider: DefaultDispatcherProvider): DispatcherProvider
}