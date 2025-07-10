package eu.darken.bluemusic.monitor.core.modules.events

import android.app.NotificationManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.hasApiLevel
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.monitor.core.modules.EventModule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RingVolumeModule @Inject constructor(
    settings: DevicesSettings,
    streamHelper: StreamHelper,
    private val notMan: NotificationManager
) : BaseVolumeModule(settings, streamHelper) {

    override val type: AudioStream.Type = AudioStream.Type.RINGTONE

    override suspend fun areRequirementsMet(): Boolean = !hasApiLevel(23) || notMan.isNotificationPolicyAccessGranted

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: RingVolumeModule): EventModule
    }
}