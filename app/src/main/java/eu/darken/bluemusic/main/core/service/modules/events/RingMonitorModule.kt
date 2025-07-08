package eu.darken.bluemusic.main.core.service.modules.events

import android.app.NotificationManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.ApiHelper
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.service.modules.EventModule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RingMonitorModule @Inject constructor(
        streamHelper: StreamHelper,
        private val notMan: NotificationManager
) : BaseMonitorModule(streamHelper) {

    override val type: AudioStream.Type
        get() = AudioStream.Type.RINGTONE

    override suspend fun areRequirementsMet(): Boolean {
        return !ApiHelper.hasMarshmallow() || notMan.isNotificationPolicyAccessGranted
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: RingMonitorModule): EventModule
    }
}
