package eu.darken.bluemusic.main.core.service;

import android.database.ContentObserver;
import android.os.Handler;
import android.util.SparseArray;
import android.util.SparseIntArray;

import javax.inject.Inject;
import javax.inject.Named;

import timber.log.Timber;


public class VolumeObserver extends ContentObserver {

    private final StreamHelper streamHelper;

    interface Callback {
        void onVolumeChanged(int streamId, int volume);
    }

    private final SparseArray<Callback> callbacks = new SparseArray<>();
    private final SparseIntArray volumes = new SparseIntArray();

    @Inject
    public VolumeObserver(@Named("VolumeObserver") Handler handler, StreamHelper streamHelper) {
        super(handler);
        this.streamHelper = streamHelper;
    }

    public void addCallback(int streamType, Callback callback) {
        callbacks.put(streamType, callback);
        final int volume = streamHelper.getCurrentVolume(streamType);
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
            int newVolume = streamHelper.getCurrentVolume(streamType);
            int oldVolume = volumes.get(streamType);
            if (newVolume != oldVolume) {
                Timber.v("Volume changed (type=%d, old=%d, new=%d)", streamType, oldVolume, newVolume);
                volumes.put(streamType, newVolume);
                callback.onVolumeChanged(streamType, newVolume);
            }
        }
    }
}
