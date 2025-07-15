package eu.darken.bluemusic.monitor.core.modules.events

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.StreamHelper
import eu.darken.bluemusic.monitor.core.modules.EventModule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicVolumeModule @Inject constructor(
    settings: DevicesSettings,
    streamHelper: StreamHelper
) : BaseVolumeModule(settings, streamHelper) {

    override val type: AudioStream.Type = AudioStream.Type.MUSIC

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: MusicVolumeModule): EventModule
    }
}