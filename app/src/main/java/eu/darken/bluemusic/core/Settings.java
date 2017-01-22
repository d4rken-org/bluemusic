package eu.darken.bluemusic.core;


import android.content.SharedPreferences;

import javax.inject.Inject;

public class Settings {
    private final SharedPreferences preferences;

    @Inject
    public Settings(SharedPreferences preferences) {this.preferences = preferences;}

    public long getFudgeDelay() {
        return Long.valueOf(preferences.getString("core.volume.delay.systemfudge", "5000"));
    }

    public boolean isBugReportingEnabled() {
        return preferences.getBoolean("core.bugreporting.enabled", true);
    }

    public boolean isEnabled() {
        return preferences.getBoolean("core.volume.enabled", true);
    }
}
