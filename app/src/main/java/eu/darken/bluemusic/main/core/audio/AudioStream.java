package eu.darken.bluemusic.main.core.audio;

import android.media.AudioManager;


public interface AudioStream {
    enum Id {
        STREAM_MUSIC(AudioManager.STREAM_MUSIC),
        STREAM_BLUETOOTH_HANDSFREE(6),
        STREAM_VOICE_CALL(AudioManager.STREAM_VOICE_CALL);
        private final int streamId;

        Id(int streamId) {this.streamId = streamId;}

        public int getId() {
            return streamId;
        }
    }

    enum Type {
        MUSIC, CALL
    }
}
