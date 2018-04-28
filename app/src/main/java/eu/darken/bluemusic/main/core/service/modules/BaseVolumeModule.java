package eu.darken.bluemusic.main.core.service.modules;

import eu.darken.bluemusic.bluetooth.core.SourceDevice;
import eu.darken.bluemusic.main.core.audio.AudioStream;
import eu.darken.bluemusic.main.core.audio.StreamHelper;
import eu.darken.bluemusic.main.core.database.ManagedDevice;
import eu.darken.bluemusic.main.core.service.ActionModule;
import eu.darken.bluemusic.settings.core.Settings;
import timber.log.Timber;

abstract class BaseVolumeModule extends ActionModule {
    private final Settings settings;
    private final StreamHelper streamHelper;

    BaseVolumeModule(Settings settings, StreamHelper streamHelper) {
        super();
        this.settings = settings;
        this.streamHelper = streamHelper;
    }

    @Override
    public void handle(ManagedDevice device, SourceDevice.Event event) {
        if (event.getType() != SourceDevice.Event.Type.CONNECTED) return;

        Float percentage = device.getVolume(getType());
        Timber.d("Desired %s volume is %s", getType(), percentage);

        if (percentage == null) return;

        if (!areRequirementsMet()) {
            Timber.d("Requirements not met!");
            return;
        }

        if (percentage != -1) {
            Long adjustmentDelay = device.getAdjustmentDelay();
            if (adjustmentDelay == null) adjustmentDelay = Settings.DEFAULT_ADJUSTMENT_DELAY;

            streamHelper.changeVolume(device.getStreamId(getType()), percentage, settings.isVolumeAdjustedVisibly(), adjustmentDelay);
        } else {
            Timber.d("Device %s has no specified target volume yet, skipping adjustments.", device);
        }
    }

    abstract AudioStream.Type getType();

}
