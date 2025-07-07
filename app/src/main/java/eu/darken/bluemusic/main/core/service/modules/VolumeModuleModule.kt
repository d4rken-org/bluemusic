package eu.darken.bluemusic.main.core.service.modules

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import eu.darken.bluemusic.main.core.service.modules.volume.VolumeLockModule
import eu.darken.bluemusic.main.core.service.modules.volume.VolumeUpdateModuleFlow

@Module
abstract class VolumeModuleModule {

    @Binds
    @IntoMap
    @VolumeModuleKey(VolumeUpdateModuleFlow::class)
    internal abstract fun volumeUpdate(module: VolumeUpdateModuleFlow): VolumeModule

    @Binds
    @IntoMap
    @VolumeModuleKey(VolumeLockModule::class)
    internal abstract fun volumeLock(module: VolumeLockModule): VolumeModule
}
