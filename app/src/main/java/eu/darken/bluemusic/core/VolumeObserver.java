package eu.darken.bluemusic.core;

import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Handler;
import android.util.SparseArray;
import android.util.SparseIntArray;

import timber.log.Timber;


public class VolumeObserver extends ContentObserver {

    private final AudioManager audioManager;

    interface Callback {
        void onVolumeChanged(int streamId, int volume);
    }

    private final SparseArray<Callback> callbacks = new SparseArray<>();
    private final SparseIntArray volumes = new SparseIntArray();

    public VolumeObserver(Handler handler, AudioManager audioManager) {
        super(handler);
        this.audioManager = audioManager;
    }

    public void addCallback(int streamType, Callback callback) {
        callbacks.put(streamType, callback);
        final int volume = audioManager.getStreamVolume(streamType);
        volumes.put(streamType, volume);
    }

    @Override
    public boolean deliverSelfNotifications() {
        return super.deliverSelfNotifications();
    }

    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);
        Timber.v("Change detected.");
        for (int i = 0; i < callbacks.size(); i++) {
            int streamType = callbacks.keyAt(i);
            Callback callback = callbacks.get(streamType);
            int newVolume = audioManager.getStreamVolume(streamType);
            int oldVolume = volumes.get(streamType);
            if (newVolume != oldVolume) {
                Timber.v("Volume changed (type=%d, old=%d, new=%d)", streamType, oldVolume, newVolume);
                volumes.put(streamType, newVolume);
                callback.onVolumeChanged(streamType, newVolume);
            }
        }
    }
}
