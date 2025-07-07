package eu.darken.bluemusic.main.core.service.modules.events

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.service.modules.EventModule
import eu.darken.bluemusic.main.core.Settings
import javax.inject.Inject

import javax.inject.Singleton

@Singleton
class MusicVolumeModule @Inject constructor(
        settings: Settings, streamHelper: StreamHelper
) : BaseVolumeModule(settings, streamHelper) {

    override val type: AudioStream.Type = AudioStream.Type.MUSIC

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: MusicVolumeModule): EventModule
    }
}