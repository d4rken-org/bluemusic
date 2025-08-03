package eu.darken.bluemusic.monitor.core.modules.connection

import android.app.NotificationManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.hasApiLevel
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.RingerModeHelper
import eu.darken.bluemusic.monitor.core.audio.StreamHelper
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RingVolumeModule @Inject constructor(
    settings: DevicesSettings,
    streamHelper: StreamHelper,
    ringerModeHelper: RingerModeHelper,
    private val notMan: NotificationManager
) : BaseVolumeModule(settings, streamHelper, ringerModeHelper) {

    override val type: AudioStream.Type = AudioStream.Type.RINGTONE

    override suspend fun areRequirementsMet(): Boolean = !hasApiLevel(23) || notMan.isNotificationPolicyAccessGranted

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: RingVolumeModule): ConnectionModule
    }
}