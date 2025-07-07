package eu.darken.bluemusic.main.core.service.modules.events

import eu.darken.bluemusic.AppComponent
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import javax.inject.Inject

@AppComponent.Scope
internal class MusicMonitorModule @Inject constructor(
        streamHelper: StreamHelper
) : BaseMonitorModule(streamHelper) {

    override val type: AudioStream.Type = AudioStream.Type.MUSIC
}
