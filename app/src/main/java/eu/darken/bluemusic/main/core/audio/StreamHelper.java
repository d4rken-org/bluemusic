package eu.darken.bluemusic.main.core.audio;

import android.media.AudioManager;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import eu.darken.bluemusic.AppComponent;
import timber.log.Timber;


@AppComponent.Scope
public class StreamHelper {
    private volatile boolean adjusting = false;
    private final AudioManager audioManager;
    private final Map<AudioStream.Id, Integer> lastUs = new HashMap<>();

    @Inject
    public StreamHelper(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    public int getCurrentVolume(AudioStream.Id id) {
        return audioManager.getStreamVolume(id.getId());
    }

    public int getMaxVolume(AudioStream.Id streamId) {
        return audioManager.getStreamMaxVolume(streamId.getId());
    }

    private synchronized void setVolume(AudioStream.Id streamId, int volume, int flags) {
        Timber.v("setVolume(streamId=%s, volume=%d, flags=%d).", streamId, volume, flags);
        adjusting = true;
        lastUs.put(streamId, volume);
        // https://stackoverflow.com/questions/6733163/notificationmanager-notify-fails-with-securityexception
        audioManager.setStreamVolume(streamId.getId(), volume, flags);
        adjusting = false;
    }

    public boolean wasUs(AudioStream.Id id, int volume) {
        return lastUs.containsKey(id) && lastUs.get(id) == volume || adjusting;
    }

    public float getVolumePercentage(AudioStream.Id streamId) {
        return (float) audioManager.getStreamVolume(streamId.getId()) / audioManager.getStreamMaxVolume(streamId.getId());
    }

    public void changeVolume(AudioStream.Id streamId, float percent, boolean visible, long delay) {
        final int currentVolume = getCurrentVolume(streamId);
        final int max = getMaxVolume(streamId);
        final int target = Math.round(max * percent);
        Timber.d("Adjusting volume (streamId=%s, target=%d, current=%d, max=%d, visible=%b, delay=%d).", streamId, target, currentVolume, max, visible, delay);
        if (currentVolume != target) {
            if (delay == 0) {
                setVolume(streamId, target, visible ? AudioManager.FLAG_SHOW_UI : 0);
            } else {
                if (currentVolume < target) {
                    for (int volumeStep = currentVolume; volumeStep <= target; volumeStep++) {
                        setVolume(streamId, volumeStep, visible ? AudioManager.FLAG_SHOW_UI : 0);
                        try { Thread.sleep(delay); } catch (InterruptedException e) { Timber.e(e, null); }
                    }
                } else {
                    for (int volumeStep = currentVolume; volumeStep >= target; volumeStep--) {
                        setVolume(streamId, volumeStep, visible ? AudioManager.FLAG_SHOW_UI : 0);
                        try { Thread.sleep(delay); } catch (InterruptedException e) { Timber.e(e, null); }
                    }
                }
            }
        } else Timber.d("Target volume of %d already set.", target);
    }

}
