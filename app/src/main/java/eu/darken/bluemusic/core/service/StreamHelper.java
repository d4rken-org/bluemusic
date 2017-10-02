package eu.darken.bluemusic.core.service;

import android.media.AudioManager;
import android.util.SparseIntArray;

import javax.inject.Inject;

import eu.darken.bluemusic.util.dagger.ApplicationScope;
import timber.log.Timber;


@ApplicationScope
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

    private synchronized void doSetVolume(int streamId, int volume, int flags) {
        adjusting = true;
        lastUs.put(streamId, volume);
        audioManager.setStreamVolume(streamId, volume, flags);
        adjusting = false;
    }

    public boolean wasUs(int streamId, int volume) {
        return lastUs.get(streamId) == volume || adjusting;
    }

    public float getVolumePercentage(int streamId) {
        return (float) audioManager.getStreamVolume(streamId) / audioManager.getStreamMaxVolume(streamId);
    }

    public void setStreamVolume(int streamId, float percent, boolean visible) {
        final int target = (int) (getMaxVolume(streamId) * percent);
        doSetVolume(streamId, target, visible ? AudioManager.FLAG_SHOW_UI : 0);
    }

    public void modifyVolume(int streamId, float percent, boolean visible) {
        final int currentVolume = getCurrentVolume(streamId);
        final int max = getMaxVolume(streamId);
        final int target = (int) (max * percent);
        if (currentVolume != target) {
            Timber.d("Adjusting volume (stream=%d, target=%d, current=%d, max=%d).", streamId, target, currentVolume, max);
            if (currentVolume < target) {
                for (int volumeStep = currentVolume; volumeStep <= target; volumeStep++) {
                    doSetVolume(streamId, volumeStep, visible ? AudioManager.FLAG_SHOW_UI : 0);
                    try { Thread.sleep(200); } catch (InterruptedException e) { Timber.e(e, null); }
                }
            } else {
                for (int volumeStep = currentVolume; volumeStep >= target; volumeStep--) {
                    doSetVolume(streamId, volumeStep, visible ? AudioManager.FLAG_SHOW_UI : 0);
                    try { Thread.sleep(200); } catch (InterruptedException e) { Timber.e(e, null); }
                }
            }
        } else Timber.d("Target volume of %d already set.", target);
    }
}
