package eu.darken.bluemusic.main.core.service

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import eu.darken.bluemusic.main.core.service.modules.VolumeModule
import eu.darken.bluemusic.main.core.service.modules.VolumeModuleKey
import eu.darken.bluemusic.main.core.service.modules.volume.VolumeUpdateModuleFlow

@Module
abstract class BlueMusicServiceFlowModule {
    
    @Binds
    @IntoMap
    @VolumeModuleKey(VolumeUpdateModuleFlow::class)
    abstract fun bindVolumeUpdateModule(module: VolumeUpdateModuleFlow): VolumeModule
}