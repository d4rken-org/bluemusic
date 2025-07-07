package eu.darken.bluemusic.main.core.service.modules.events

import android.app.NotificationManager
import eu.darken.bluemusic.AppComponent
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.settings.core.Settings
import eu.darken.bluemusic.util.ApiHelper
import javax.inject.Inject

@AppComponent.Scope
class NotificationVolumeModule @Inject constructor(
        settings: Settings,
        streamHelper: StreamHelper,
        private val notMan: NotificationManager
) : BaseVolumeModule(settings, streamHelper) {

    override val type: AudioStream.Type = AudioStream.Type.NOTIFICATION

    override fun areRequirementsMet(): Boolean = !ApiHelper.hasMarshmallow() || notMan.isNotificationPolicyAccessGranted
}