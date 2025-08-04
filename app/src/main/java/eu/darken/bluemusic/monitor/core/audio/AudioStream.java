package eu.darken.bluemusic.monitor.core.audio;

import android.media.AudioManager;


public interface AudioStream {
    // Special volume values for sound modes
    float SOUND_MODE_SILENT = -2f;
    float SOUND_MODE_VIBRATE = -3f;
    
    enum Id {
        STREAM_MUSIC(AudioManager.STREAM_MUSIC),
        STREAM_BLUETOOTH_HANDSFREE(6),
        STREAM_VOICE_CALL(AudioManager.STREAM_VOICE_CALL),
        STREAM_NOTIFICATION(AudioManager.STREAM_NOTIFICATION),
        STREAM_RINGTONE(AudioManager.STREAM_RING),
        STREAM_ALARM(AudioManager.STREAM_ALARM);
        private final int streamId;

        Id(int streamId) {this.streamId = streamId;}

        public int getId() {
            return streamId;
        }
    }

    enum Type {
        MUSIC, CALL, RINGTONE, NOTIFICATION, ALARM
    }
}
