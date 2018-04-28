package eu.darken.bluemusic.main.core.service;

import eu.darken.bluemusic.bluetooth.core.SourceDevice;
import eu.darken.bluemusic.main.core.database.ManagedDevice;

public abstract class ActionModule {

    public boolean areRequirementsMet() {
        return true;
    }

    public abstract void handle(ManagedDevice device, SourceDevice.Event event);

    /**
     * When should this module run, lower = earlier, higher = later.
     * Modules with the same priority run in parallel
     */
    public int getPriority() {
        // Default
        return 10;
    }
}
