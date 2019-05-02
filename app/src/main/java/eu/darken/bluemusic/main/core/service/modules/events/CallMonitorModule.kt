package eu.darken.bluemusic.main.core.service.modules.events

import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.service.BlueMusicServiceComponent
import javax.inject.Inject

@BlueMusicServiceComponent.Scope
internal class CallMonitorModule @Inject constructor(
        streamHelper: StreamHelper
) : BaseMonitorModule(streamHelper) {
    override val type: AudioStream.Type
        get() = AudioStream.Type.MUSIC
}
