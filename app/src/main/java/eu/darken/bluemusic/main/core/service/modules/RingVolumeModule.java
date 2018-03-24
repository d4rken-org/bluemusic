package eu.darken.bluemusic.main.core.service.modules;

import javax.inject.Inject;

import eu.darken.bluemusic.main.core.audio.AudioStream;
import eu.darken.bluemusic.main.core.audio.StreamHelper;
import eu.darken.bluemusic.main.core.service.BlueMusicServiceComponent;
import eu.darken.bluemusic.settings.core.Settings;

@BlueMusicServiceComponent.Scope
public class RingVolumeModule extends BaseVolumeModule {

    @Inject
    public RingVolumeModule(Settings settings, StreamHelper streamHelper) {
        super(settings, streamHelper);
    }

    @Override
    AudioStream.Type getType() {
        return AudioStream.Type.RINGTONE;
    }
}
