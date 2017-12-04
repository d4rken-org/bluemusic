package eu.darken.bluemusic.settings.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import eu.darken.bluemusic.AppComponent;
import eu.darken.bluemusic.main.core.service.StreamHelper;
import timber.log.Timber;

@AppComponent.Scope
public class StreamSettings {
    private final Context context;
    private static final String PREFFILE = "default_volumes.xml";
    private final SharedPreferences preferences;
    private final StreamHelper streamHelper;

    @Inject
    public StreamSettings(Context context, StreamHelper streamHelper) {
        this.context = context;
        preferences = context.getSharedPreferences(PREFFILE, Context.MODE_PRIVATE);
        this.streamHelper = streamHelper;
    }

    public float getVolume(int streamId) {
        final float percentage = preferences.getFloat(buildKey(streamId), streamHelper.getVolumePercentage(streamId));
        Timber.v("getVolume(streamId=%d): %f", streamId, percentage);
        return percentage;
    }

    public void saveVolume(int streamId, float percentage) {
        Timber.v("saveVolume(streamid=%d, percentage=%f", streamId, percentage);
        preferences.edit().putFloat(buildKey(streamId), percentage).apply();
    }

    private String buildKey(int streamId) {
        return "stream." + streamId + ".volume.percentage";
    }

    public void save(Map<Integer, Float> volumeMap) {
        for (Map.Entry<Integer, Float> entry : volumeMap.entrySet()) {
            saveVolume(entry.getKey(), entry.getValue());
        }
    }

    public Map<Integer, Float> load(List<Integer> streamIds) {
        Map<Integer, Float> volumeMap = new HashMap<>();
        for (Integer streamId : streamIds) {
            volumeMap.put(streamId, getVolume(streamId));
        }
        return volumeMap;
    }
}
