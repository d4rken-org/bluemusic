package eu.darken.bluemusic.settings.core;


import android.content.SharedPreferences;
import android.view.KeyEvent;

import javax.inject.Inject;

public class Settings {
    private static final String PREFKEY_VOLUMELISTENER = "core.volume.changelistener";

    public static final String PREFKEY_VISIBLE_ADJUSTMENTS = "core.volume.visibleadjustments";
    public static final String PREFKEY_AUTOPLAY_KEYCODE = "core.autoplay.keycode";
    public static final String PREFKEY_SPEAKER_AUTOSAVE = "core.speaker.autosave";

    private static final String PREFKEY_INSTALLTIME = "core.metrics.installtime";
    private static final String PREFKEY_LAUNCHCOUNT = "core.metrics.launchcount";
    private static final String PREFKEY_BOOT_RESTORE = "core.onboot.restore";

    private static final String PREFKEY_BATTERYOPTIMIZATIONHINT_DISMISSED = "core.batteryoptimizations.dismissed";

    private static final String PREFKEY_ONBOARDING_INTRODONE = "core.onboarding.introdone";

    private static final String PREFKEY_ADVANCED_EXCLUDE_HEALTHDEVICES = "core.advanced.exclude.healthdevices";
    private static final String PREFKEY_ADVANCED_EXCLUDE_GATT = "core.advanced.exclude.gatt";
    private static final String PREFKEY_ADVANCED_EXCLUDE_GATTSERVER = "core.advanced.exclude.gattserver";

    public static final String PREFKEY_BUGREPORTING = "core.bugreporting.enabled";

    private static final String PREFKEY_CORE_ENABLED = "core.enabled";
    public static final long DEFAULT_REACTION_DELAY = 8000;
    public static final long DEFAULT_ADJUSTMENT_DELAY = 250;
    private final SharedPreferences preferences;

    @Inject
    public Settings(SharedPreferences preferences) {
        this.preferences = preferences;
        if (!preferences.contains(PREFKEY_INSTALLTIME)) {
            preferences.edit().putLong(PREFKEY_INSTALLTIME, System.currentTimeMillis()).apply();
        }
        int currentLaunchCount = preferences.getInt(PREFKEY_LAUNCHCOUNT, 0);
        preferences.edit().putInt(PREFKEY_LAUNCHCOUNT, ++currentLaunchCount).apply();
    }

    public boolean isBugReportingEnabled() {
        return preferences.getBoolean(PREFKEY_BUGREPORTING, true);
    }

    public boolean isVolumeAdjustedVisibly() {
        return preferences.getBoolean(PREFKEY_VISIBLE_ADJUSTMENTS, true);
    }

    public boolean isVolumeChangeListenerEnabled() {
        return preferences.getBoolean(PREFKEY_VOLUMELISTENER, false);
    }

    public boolean isEnabled() {
        return preferences.getBoolean(PREFKEY_CORE_ENABLED, true);
    }

    public int getAutoplayKeycode() {
        return preferences.getInt(PREFKEY_AUTOPLAY_KEYCODE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    public void setAutoplayKeycode(int keycode) {
        preferences.edit().putInt(PREFKEY_AUTOPLAY_KEYCODE, keycode).apply();
    }

    public boolean isSpeakerAutoSaveEnabled() {
        return preferences.getBoolean(PREFKEY_SPEAKER_AUTOSAVE, false);
    }

    public int getLaunchCount() {
        return preferences.getInt(PREFKEY_LAUNCHCOUNT, 0);
    }

    public long getInstallTime() {
        return preferences.getLong(PREFKEY_INSTALLTIME, System.currentTimeMillis());
    }

    public boolean isBootRestoreEnabled() {
        return preferences.getBoolean(PREFKEY_BOOT_RESTORE, true);
    }

    public boolean isShowOnboarding() {
        return preferences.getBoolean(PREFKEY_ONBOARDING_INTRODONE, true);
    }

    public void setShowOnboarding(boolean show) {
        preferences.edit().putBoolean(PREFKEY_ONBOARDING_INTRODONE, show).apply();
    }

    public boolean isHealthDeviceExcluded() {
        return preferences.getBoolean(PREFKEY_ADVANCED_EXCLUDE_HEALTHDEVICES, true);
    }

    public boolean isGATTExcluded() {
        return preferences.getBoolean(PREFKEY_ADVANCED_EXCLUDE_GATT, false);
    }

    public boolean isGATTServerExcluded() {
        return preferences.getBoolean(PREFKEY_ADVANCED_EXCLUDE_GATTSERVER, false);
    }

    public boolean isBatterySavingHintDismissed() {
        return preferences.getBoolean(PREFKEY_BATTERYOPTIMIZATIONHINT_DISMISSED, false);
    }

    public void setBatterySavingHintDismissed(boolean dismissed) {
        preferences.edit().putBoolean(PREFKEY_BATTERYOPTIMIZATIONHINT_DISMISSED, dismissed).apply();
    }
}
