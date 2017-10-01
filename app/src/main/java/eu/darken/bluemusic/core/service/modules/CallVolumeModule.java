package eu.darken.bluemusic.core.service.modules;

import javax.inject.Inject;

import eu.darken.bluemusic.core.Settings;
import eu.darken.bluemusic.core.bluetooth.SourceDevice;
import eu.darken.bluemusic.core.database.DeviceManager;
import eu.darken.bluemusic.core.database.ManagedDevice;
import eu.darken.bluemusic.core.service.ActionModule;
import eu.darken.bluemusic.core.service.StreamHelper;
import eu.darken.bluemusic.util.dagger.ServiceScope;
import timber.log.Timber;

@ServiceScope
public class CallVolumeModule extends ActionModule {
    private final StreamHelper streamHelper;

    @Inject
    public CallVolumeModule(DeviceManager deviceManager, StreamHelper streamHelper) {
        super(deviceManager);
        this.streamHelper = streamHelper;
    }

    @Override
    public void handle(ManagedDevice device, SourceDevice.Event event) {
        if (event.getType() != SourceDevice.Event.Type.CONNECTED) return;
        Timber.d("Desired CALL volume is %s", device.getCallVolume());
        if (device.getCallVolume() == null) {
            Timber.d("Call volume adjustment is disabled.");
            return;
        }

        Float percentageVoice = device.getCallVolume();
        if (percentageVoice != -1) {
            try {
                Long delay = device.getActionDelay();
                if (delay == null) delay = Settings.DEFAULT_DELAY;
                Timber.d("Delaying adjustment by %s ms.", delay);
                Thread.sleep(delay);
            } catch (InterruptedException e) { Timber.e(e, null); }

            streamHelper.modifyStream(streamHelper.getCallId(), device.getRealVoiceVolume(), device.getMaxVoiceVolume());
        } else {
            Timber.d("Device %s has no specified target volume yet, skipping adjustments.", device);
        }
    }
}
