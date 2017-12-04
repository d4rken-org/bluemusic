package eu.darken.bluemusic.main.core.service;

import android.media.AudioManager;
import android.util.SparseIntArray;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import eu.darken.bluemusic.AppComponent;
import timber.log.Timber;


@AppComponent.Scope
public class StreamHelper {
    private volatile boolean adjusting = false;
    private final AudioManager audioManager;
    private final SparseIntArray lastUs = new SparseIntArray();

    @Inject
    public StreamHelper(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    public int getMusicId() {
        return AudioManager.STREAM_MUSIC;
    }

    public int getCallId() {
        return 6;
    }

    public int getCurrentVolume(int streamType) {
        return audioManager.getStreamVolume(streamType);
    }

    public int getMaxVolume(int streamId) {
        return audioManager.getStreamMaxVolume(streamId);
    }

    private synchronized void setVolume(int streamId, int volume, int flags) {
        Timber.v("setVolume(streamId=%d, volume=%d, flags=%d).", streamId, volume, flags);
        adjusting = true;
        lastUs.put(streamId, volume);
        // https://stackoverflow.com/questions/6733163/notificationmanager-notify-fails-with-securityexception
        audioManager.setStreamVolume(streamId, volume, flags);
        adjusting = false;
    }

    public boolean wasUs(int streamId, int volume) {
        return lastUs.get(streamId) == volume || adjusting;
    }

    public float getVolumePercentage(int streamId) {
        return (float) audioManager.getStreamVolume(streamId) / audioManager.getStreamMaxVolume(streamId);
    }

    public void changeVolume(int streamId, float percent, boolean visible, long delay) {
        final int currentVolume = getCurrentVolume(streamId);
        final int max = getMaxVolume(streamId);
        final int target = Math.round(max * percent);
        Timber.d("Adjusting volume (streamId=%d, target=%d, current=%d, max=%d, visible=%b).", streamId, target, currentVolume, max, visible);
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

    public List<Integer> getStreamIds() {
        return Arrays.asList(getMusicId(), getCallId());
    }

    public Map<Integer, Float> getVolumes() {
        Map<Integer, Float> volumeMap = new HashMap<>();
        for (Integer streamId : getStreamIds()) {
            final float current = getVolumePercentage(streamId);
            volumeMap.put(streamId, current);
        }
        return volumeMap;
    }

    public void setVolumes(Map<Integer, Float> volumeMap, boolean visible, long delay) {
        for (Map.Entry<Integer, Float> entry : volumeMap.entrySet()) {
            changeVolume(entry.getKey(), entry.getValue(), visible, delay);
        }
    }
}
