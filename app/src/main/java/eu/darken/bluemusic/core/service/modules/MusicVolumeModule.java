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
public class MusicVolumeModule extends ActionModule {
    private final Settings settings;
    private final StreamHelper streamHelper;

    @Inject
    public MusicVolumeModule(DeviceManager deviceManager, Settings settings, StreamHelper streamHelper) {
        super(deviceManager);
        this.settings = settings;
        this.streamHelper = streamHelper;
    }

    @Override
    public void handle(ManagedDevice device, SourceDevice.Event event) {
        if (event.getType() != SourceDevice.Event.Type.CONNECTED) return;
        Timber.d("Desired MUSIC volume is %s", device.getCallVolume());
        if (device.getMusicVolume() == null) {
            Timber.d("Music volume adjustment is disabled.");
            return;
        }

        Float percentageMusic = device.getMusicVolume();
        if (percentageMusic != -1) {
            try {
                Long delay = device.getActionDelay();
                if (delay == null) delay = Settings.DEFAULT_DELAY;
                Timber.d("Delaying adjustment by %s ms.", delay);
                Thread.sleep(delay);
            } catch (InterruptedException e) { Timber.e(e, null); }

            streamHelper.setVolumeGradually(streamHelper.getMusicId(), percentageMusic, settings.isVolumeAdjustedVisibly());
        } else {
            Timber.d("Device %s has no specified target volume yet, skipping adjustments.", device);
        }
    }

}
