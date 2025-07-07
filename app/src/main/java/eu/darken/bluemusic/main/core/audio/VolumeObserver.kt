package eu.darken.bluemusic.main.core.audio;

import android.database.ContentObserver;
import android.os.Handler;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import timber.log.Timber;


public class VolumeObserver extends ContentObserver {

    private final StreamHelper streamHelper;

    public interface Callback {
        void onVolumeChanged(AudioStream.Id streamId, int volume);
    }

    private final Map<AudioStream.Id, Callback> callbacks = new HashMap<>();
    private final Map<AudioStream.Id, Integer> volumes = new HashMap<>();

    @Inject
    public VolumeObserver(@Named("VolumeObserver") Handler handler, StreamHelper streamHelper) {
        super(handler);
        this.streamHelper = streamHelper;
    }

    public void addCallback(AudioStream.Id id, Callback callback) {
        callbacks.put(id, callback);
        final int volume = streamHelper.getCurrentVolume(id);
        volumes.put(id, volume);
    }

    @Override
    public boolean deliverSelfNotifications() {
        return false;
    }

    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);
        Timber.v("Change detected.");
        for (Map.Entry<AudioStream.Id, Callback> entry : callbacks.entrySet()) {
            final AudioStream.Id id = entry.getKey();
            Callback callback = callbacks.get(id);
            int newVolume = streamHelper.getCurrentVolume(id);
            int oldVolume = volumes.containsKey(id) ? volumes.get(id) : -1;
            if (newVolume != oldVolume) {
                Timber.v("Volume changed (type=%s, old=%d, new=%d)", id, oldVolume, newVolume);
                volumes.put(id, newVolume);
                callback.onVolumeChanged(id, newVolume);
            }
        }
    }
}
