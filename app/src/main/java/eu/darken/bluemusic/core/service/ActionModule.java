package eu.darken.bluemusic.core.service;

import eu.darken.bluemusic.core.Settings;
import eu.darken.bluemusic.core.bluetooth.SourceDevice;
import eu.darken.bluemusic.core.database.ManagedDevice;
import timber.log.Timber;

public abstract class ActionModule {

    public abstract void handle(ManagedDevice device, SourceDevice.Event event);
    
    protected void waitAdjustmentDelay(ManagedDevice device) {
        try {
            Long reactionDelay = device.getActionDelay();
            if (reactionDelay == null) reactionDelay = Settings.DEFAULT_REACTION_DELAY;

            Timber.tag(getClass().getSimpleName()).d("Delaying adjustment by %s ms.", reactionDelay);
            Thread.sleep(reactionDelay);
        } catch (InterruptedException e) { Timber.e(e, null); }
    }
}
