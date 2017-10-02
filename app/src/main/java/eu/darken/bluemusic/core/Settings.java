package eu.darken.bluemusic.core;


import android.content.SharedPreferences;

import javax.inject.Inject;

public class Settings {
    static final String PREFKEY_VOLUMELISTENER = "core.volume.changelistener";
    public static final long DEFAULT_REACTION_DELAY = 5000;
    public static final long DEFAULT_ADJUSTMENT_DELAY = 250;
    private final SharedPreferences preferences;

    @Inject
    public Settings(SharedPreferences preferences) {this.preferences = preferences;}

    public boolean isBugReportingEnabled() {
        return preferences.getBoolean("core.bugreporting.enabled", true);
    }

    public boolean isVolumeAdjustedVisibly() {
        return preferences.getBoolean("core.volume.visibleadjustments", true);
    }

    public boolean isVolumeChangeListenerEnabled() {
        return preferences.getBoolean(PREFKEY_VOLUMELISTENER, false);
    }

    public boolean isEnabled() {
        return preferences.getBoolean("core.enabled", true);
    }
}
