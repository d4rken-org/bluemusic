package eu.darken.bluemusic.main.core.service.modules;

import android.app.NotificationManager;

import javax.inject.Inject;

import eu.darken.bluemusic.main.core.audio.AudioStream;
import eu.darken.bluemusic.main.core.audio.StreamHelper;
import eu.darken.bluemusic.main.core.service.BlueMusicServiceComponent;
import eu.darken.bluemusic.settings.core.Settings;
import eu.darken.bluemusic.util.ApiHelper;

@BlueMusicServiceComponent.Scope
public class RingVolumeModule extends BaseVolumeModule {

    private final NotificationManager notMan;

    @Inject
    public RingVolumeModule(Settings settings, StreamHelper streamHelper, NotificationManager notMan) {
        super(settings, streamHelper);
        this.notMan = notMan;
    }

    @Override
    AudioStream.Type getType() {
        return AudioStream.Type.RINGTONE;
    }

    @Override
    public boolean areRequirementsMet() {
        return !ApiHelper.hasMarshmallow() || notMan.isNotificationPolicyAccessGranted();
    }
}
