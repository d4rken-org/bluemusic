package eu.darken.bluemusic.main.core.service.modules;

import android.support.annotation.Nullable;

import javax.inject.Inject;

import eu.darken.bluemusic.main.core.database.ManagedDevice;
import eu.darken.bluemusic.main.core.service.BlueMusicServiceComponent;
import eu.darken.bluemusic.main.core.service.StreamHelper;
import eu.darken.bluemusic.settings.core.Settings;

@BlueMusicServiceComponent.Scope
public class MusicVolumeModule extends BaseVolumeModule {
    private final StreamHelper streamHelper;

    @Inject
    public MusicVolumeModule(Settings settings, StreamHelper streamHelper) {
        super(settings, streamHelper);
        this.streamHelper = streamHelper;
    }

    @Nullable
    @Override
    Float getDesiredVolume(ManagedDevice device) {
        return device.getMusicVolume();
    }

    @Override
    String getStreamName() {
        return "Music";
    }

    @Override
    int getStreamId() {
        return streamHelper.getMusicId();
    }

}
