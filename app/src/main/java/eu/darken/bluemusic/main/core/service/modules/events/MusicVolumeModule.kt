package eu.darken.bluemusic.main.core.service.modules.events

import eu.darken.bluemusic.AppComponent
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.settings.core.Settings
import javax.inject.Inject

@AppComponent.Scope
class MusicVolumeModule @Inject constructor(
        settings: Settings, streamHelper: StreamHelper
) : BaseVolumeModule(settings, streamHelper) {

    override val type: AudioStream.Type = AudioStream.Type.MUSIC
}