package eu.darken.bluemusic.core.service;

import android.media.AudioManager;
import android.util.SparseIntArray;

import javax.inject.Inject;

import eu.darken.bluemusic.util.dagger.ApplicationScope;


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

    public int getVoiceId() {
        return 6;
    }

    public int getVolume(int streamType) {
        return audioManager.getStreamVolume(streamType);
    }

    public int getMaxVolume(int streamId) {
        return audioManager.getStreamMaxVolume(streamId);
    }

    public synchronized void setStreamVolume(int streamId, int volume, int flags) {
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


}
