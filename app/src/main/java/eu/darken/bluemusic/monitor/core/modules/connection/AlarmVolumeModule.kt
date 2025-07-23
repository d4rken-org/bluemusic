package eu.darken.bluemusic.monitor.core.modules.connection

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.StreamHelper
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmVolumeModule @Inject constructor(
    devicesSettings: DevicesSettings,
        streamHelper: StreamHelper
) : BaseVolumeModule(devicesSettings, streamHelper) {

    override val type: AudioStream.Type = AudioStream.Type.ALARM

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: AlarmVolumeModule): ConnectionModule
    }
}