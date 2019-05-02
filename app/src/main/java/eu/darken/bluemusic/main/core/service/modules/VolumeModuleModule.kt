package eu.darken.bluemusic.main.core.service.modules

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import eu.darken.bluemusic.main.core.service.modules.volume.VolumeLockModule
import eu.darken.bluemusic.main.core.service.modules.volume.VolumeUpdateModule

@Module
abstract class VolumeModuleModule {

    @Binds
    @IntoMap
    @VolumeModuleKey(VolumeUpdateModule::class)
    internal abstract fun volumeUpdate(module: VolumeUpdateModule): VolumeModule

    @Binds
    @IntoMap
    @VolumeModuleKey(VolumeLockModule::class)
    internal abstract fun volumeLock(module: VolumeLockModule): VolumeModule
}
