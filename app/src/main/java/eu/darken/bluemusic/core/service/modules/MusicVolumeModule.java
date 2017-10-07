package eu.darken.bluemusic.core.service.modules;

import android.support.annotation.Nullable;

import javax.inject.Inject;

import eu.darken.bluemusic.core.database.ManagedDevice;
import eu.darken.bluemusic.core.service.StreamHelper;
import eu.darken.bluemusic.core.settings.Settings;
import eu.darken.bluemusic.util.dagger.ServiceScope;

@ServiceScope
public class MusicVolumeModule extends BaseVolumeModel {
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
