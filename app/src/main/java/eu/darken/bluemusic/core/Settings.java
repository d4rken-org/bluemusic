package eu.darken.bluemusic.core;


import android.content.SharedPreferences;

import javax.inject.Inject;

public class Settings {
    public static final long DEFAULT_DELAY = 5000;
    private final SharedPreferences preferences;

    @Inject
    public Settings(SharedPreferences preferences) {this.preferences = preferences;}

    public boolean isBugReportingEnabled() {
        return preferences.getBoolean("core.bugreporting.enabled", true);
    }

    public boolean isVolumeAdjustedVisibly() {
        return preferences.getBoolean("core.volume.visibleadjustments", true);
    }

    public boolean isEnabled() {
        return preferences.getBoolean("core.volume.enabled", true);
    }
}
