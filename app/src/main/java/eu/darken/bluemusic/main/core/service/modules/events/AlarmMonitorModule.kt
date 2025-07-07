package eu.darken.bluemusic.main.core.service.modules.events

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.service.modules.EventModule
import javax.inject.Inject

import javax.inject.Singleton

@Singleton
internal class AlarmMonitorModule @Inject constructor(
        streamHelper: StreamHelper
) : BaseMonitorModule(streamHelper) {
    override val type: AudioStream.Type
        get() = AudioStream.Type.ALARM

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: AlarmMonitorModule): EventModule
    }
}
