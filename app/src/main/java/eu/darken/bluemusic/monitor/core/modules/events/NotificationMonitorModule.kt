package eu.darken.bluemusic.monitor.core.modules.events

import android.app.NotificationManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.hasApiLevel
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.StreamHelper
import eu.darken.bluemusic.monitor.core.modules.EventModule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class NotificationMonitorModule @Inject constructor(
        streamHelper: StreamHelper,
        private val notMan: NotificationManager
) : BaseMonitorModule(streamHelper) {

    override val type: AudioStream.Type
        get() = AudioStream.Type.NOTIFICATION

    override suspend fun areRequirementsMet(): Boolean {
        return !hasApiLevel(23) || notMan.isNotificationPolicyAccessGranted
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: NotificationMonitorModule): EventModule
    }
}
