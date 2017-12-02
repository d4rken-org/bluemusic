package eu.darken.bluemusic.main.core.service.modules;

import android.support.annotation.Nullable;

import javax.inject.Inject;

import eu.darken.bluemusic.main.core.database.ManagedDevice;
import eu.darken.bluemusic.main.core.service.BlueMusicServiceComponent;
import eu.darken.bluemusic.main.core.service.StreamHelper;
import eu.darken.bluemusic.settings.core.Settings;

@BlueMusicServiceComponent.Scope
public class CallVolumeModule extends BaseVolumeModule {
    private final StreamHelper streamHelper;

    @Inject
    public CallVolumeModule(Settings settings, StreamHelper streamHelper) {
        super(settings, streamHelper);
        this.streamHelper = streamHelper;
    }

    @Nullable
    @Override
    Float getDesiredVolume(ManagedDevice device) {
        return device.getCallVolume();
    }

    @Override
    String getStreamName() {
        return "Call";
    }

    @Override
    int getStreamId() {
        return streamHelper.getCallId();
    }
}
