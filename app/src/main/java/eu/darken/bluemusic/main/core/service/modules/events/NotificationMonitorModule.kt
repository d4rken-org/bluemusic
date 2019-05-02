package eu.darken.bluemusic.main.core.service.modules.events

import android.app.NotificationManager
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.service.BlueMusicServiceComponent
import eu.darken.bluemusic.util.ApiHelper
import javax.inject.Inject

@BlueMusicServiceComponent.Scope
internal class NotificationMonitorModule @Inject constructor(
        streamHelper: StreamHelper,
        private val notMan: NotificationManager
) : BaseMonitorModule(streamHelper) {

    override val type: AudioStream.Type
        get() = AudioStream.Type.NOTIFICATION

    override fun areRequirementsMet(): Boolean {
        return !ApiHelper.hasMarshmallow() || notMan.isNotificationPolicyAccessGranted
    }
}
