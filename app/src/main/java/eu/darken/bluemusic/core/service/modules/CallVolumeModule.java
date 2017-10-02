package eu.darken.bluemusic.core.service.modules;

import android.support.annotation.Nullable;

import javax.inject.Inject;

import eu.darken.bluemusic.core.Settings;
import eu.darken.bluemusic.core.database.DeviceManager;
import eu.darken.bluemusic.core.database.ManagedDevice;
import eu.darken.bluemusic.core.service.StreamHelper;
import eu.darken.bluemusic.util.dagger.ServiceScope;

@ServiceScope
public class CallVolumeModule extends BaseVolumeModel {
    private final StreamHelper streamHelper;

    @Inject
    public CallVolumeModule(DeviceManager deviceManager, Settings settings, StreamHelper streamHelper) {
        super(deviceManager, settings, streamHelper);
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
