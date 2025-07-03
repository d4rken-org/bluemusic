package eu.darken.bluemusic.common.dagger

import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.Module
import eu.darken.bluemusic.ui.ViewModelBinder

@Module(includes = [ViewModelBinder::class])
abstract class ViewModelModule {
    
    @Binds
    abstract fun bindViewModelFactory(factory: ViewModelFactory): ViewModelProvider.Factory
}