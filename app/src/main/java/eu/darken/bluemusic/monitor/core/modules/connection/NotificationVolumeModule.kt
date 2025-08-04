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
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationVolumeModule @Inject constructor(
    settings: DevicesSettings,
    volumeTool: VolumeTool,
    private val notMan: NotificationManager
) : BaseVolumeModule(settings, volumeTool) {

    override val type: AudioStream.Type = AudioStream.Type.NOTIFICATION

    override suspend fun areRequirementsMet(): Boolean = !hasApiLevel(23) || notMan.isNotificationPolicyAccessGranted

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: NotificationVolumeModule): ConnectionModule
    }
}