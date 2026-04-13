package eu.darken.bluemusic.monitor.core.modules.connection

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeObserver
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import eu.darken.bluemusic.monitor.core.modules.volume.VolumeObservationGate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicVolumeModule @Inject constructor(
    volumeTool: VolumeTool,
    volumeObserver: VolumeObserver,
    observationGate: VolumeObservationGate,
) : BaseVolumeModule(volumeTool, volumeObserver, observationGate) {

    override val type: AudioStream.Type = AudioStream.Type.MUSIC

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: MusicVolumeModule): ConnectionModule
    }
}