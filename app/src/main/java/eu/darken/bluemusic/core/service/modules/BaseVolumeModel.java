package eu.darken.bluemusic.core.service.modules;

import android.support.annotation.Nullable;

import eu.darken.bluemusic.core.bluetooth.SourceDevice;
import eu.darken.bluemusic.core.database.ManagedDevice;
import eu.darken.bluemusic.core.service.ActionModule;
import eu.darken.bluemusic.core.service.StreamHelper;
import eu.darken.bluemusic.core.settings.Settings;
import timber.log.Timber;

abstract class BaseVolumeModel extends ActionModule {
    private final Settings settings;
    private final StreamHelper streamHelper;

    BaseVolumeModel(Settings settings, StreamHelper streamHelper) {
        super();
        this.settings = settings;
        this.streamHelper = streamHelper;
    }

    @Override
    public void handle(ManagedDevice device, SourceDevice.Event event) {
        if (event.getType() != SourceDevice.Event.Type.CONNECTED) return;
        Timber.d("Desired %s volume is %s", getStreamName(), getDesiredVolume(device));
        if (getDesiredVolume(device) == null) {
            Timber.d("%s volume adjustment is disabled.", getStreamName());
            return;
        }
        Float percentage = getDesiredVolume(device);
        if (percentage != null && percentage != -1) {
            waitAdjustmentDelay(device);

            Long adjustmentDelay = device.getAdjustmentDelay();
            if (adjustmentDelay == null) adjustmentDelay = Settings.DEFAULT_ADJUSTMENT_DELAY;

            streamHelper.setVolume(getStreamId(), percentage, settings.isVolumeAdjustedVisibly(), adjustmentDelay);
        } else {
            Timber.d("Device %s has no specified target volume yet, skipping adjustments.", device);
        }
    }

    @Nullable
    abstract Float getDesiredVolume(ManagedDevice device);

    abstract String getStreamName();

    abstract int getStreamId();
}
