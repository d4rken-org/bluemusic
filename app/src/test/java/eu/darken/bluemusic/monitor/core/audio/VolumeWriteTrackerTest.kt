package eu.darken.bluemusic.monitor.core.audio

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class VolumeWriteTrackerTest : BaseTest() {

    private lateinit var tracker: VolumeWriteTracker
    private var fakeTime = 0L

    @BeforeEach
    fun setup() {
        fakeTime = 1000L
        tracker = VolumeWriteTracker(clock = { fakeTime })
    }

    @Nested
    inner class MirroredPeer {
        @Test
        fun `VOICE_CALL write marks BLUETOOTH_HANDSFREE as us`() {
            tracker.onWriteStarted(AudioStream.Id.STREAM_VOICE_CALL, 10)
            tracker.onWriteFinished()

            tracker.wasUs(AudioStream.Id.STREAM_VOICE_CALL, 10) shouldBe true
            tracker.wasUs(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 10) shouldBe true
        }

        @Test
        fun `BLUETOOTH_HANDSFREE write marks VOICE_CALL as us`() {
            tracker.onWriteStarted(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 7)
            tracker.onWriteFinished()

            tracker.wasUs(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 7) shouldBe true
            tracker.wasUs(AudioStream.Id.STREAM_VOICE_CALL, 7) shouldBe true
        }

        @Test
        fun `MUSIC write does not mirror to other streams`() {
            tracker.onWriteStarted(AudioStream.Id.STREAM_MUSIC, 12)
            tracker.onWriteFinished()

            tracker.wasUs(AudioStream.Id.STREAM_MUSIC, 12) shouldBe true
            tracker.wasUs(AudioStream.Id.STREAM_VOICE_CALL, 12) shouldBe false
            tracker.wasUs(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 12) shouldBe false
            tracker.wasUs(AudioStream.Id.STREAM_ALARM, 12) shouldBe false
        }
    }

    @Nested
    inner class WriteExpiry {
        @Test
        fun `wasUs returns true within TTL`() {
            tracker.onWriteStarted(AudioStream.Id.STREAM_MUSIC, 5)
            tracker.onWriteFinished()

            fakeTime += 1500
            tracker.wasUs(AudioStream.Id.STREAM_MUSIC, 5) shouldBe true
        }

        @Test
        fun `wasUs returns false after TTL expires`() {
            tracker.onWriteStarted(AudioStream.Id.STREAM_MUSIC, 5)
            tracker.onWriteFinished()

            fakeTime += 2500
            tracker.wasUs(AudioStream.Id.STREAM_MUSIC, 5) shouldBe false
        }

        @Test
        fun `mirrored entry also expires`() {
            tracker.onWriteStarted(AudioStream.Id.STREAM_VOICE_CALL, 8)
            tracker.onWriteFinished()

            fakeTime += 2500
            tracker.wasUs(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 8) shouldBe false
        }
    }

    @Nested
    inner class PendingConsumption {
        @Test
        fun `wasUs consumes matching pending write`() {
            tracker.onWriteStarted(AudioStream.Id.STREAM_MUSIC, 10)
            tracker.onWriteFinished()

            tracker.wasUs(AudioStream.Id.STREAM_MUSIC, 10) shouldBe true
            tracker.wasUs(AudioStream.Id.STREAM_MUSIC, 10) shouldBe false
        }

        @Test
        fun `consuming pending write does not drop recent target`() {
            tracker.onWriteStarted(AudioStream.Id.STREAM_MUSIC, 10)
            tracker.onWriteFinished()

            tracker.wasUs(AudioStream.Id.STREAM_MUSIC, 10) shouldBe true
            tracker.hasRecentTarget(AudioStream.Id.STREAM_MUSIC, 10) shouldBe true
        }

        @Test
        fun `mismatched observed level clears stale pending write`() {
            tracker.onWriteStarted(AudioStream.Id.STREAM_MUSIC, 11)
            tracker.onWriteFinished()

            tracker.wasUs(AudioStream.Id.STREAM_MUSIC, 7) shouldBe false
            tracker.wasUs(AudioStream.Id.STREAM_MUSIC, 11) shouldBe false
        }

        @Test
        fun `after self event is consumed return to same level is not self`() {
            tracker.onWriteStarted(AudioStream.Id.STREAM_MUSIC, 11)
            tracker.onWriteFinished()

            tracker.wasUs(AudioStream.Id.STREAM_MUSIC, 11) shouldBe true
            tracker.wasUs(AudioStream.Id.STREAM_MUSIC, 7) shouldBe false
            tracker.wasUs(AudioStream.Id.STREAM_MUSIC, 11) shouldBe false
        }
    }

    @Nested
    inner class RecentTargets {
        @Test
        fun `already at target records direct stream only`() {
            tracker.rememberCurrentTarget(AudioStream.Id.STREAM_VOICE_CALL, 10)

            tracker.hasRecentTarget(AudioStream.Id.STREAM_VOICE_CALL, 10) shouldBe true
            tracker.hasRecentTarget(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 10) shouldBe false
        }

        @Test
        fun `mirrored peer also keeps recent target for real writes`() {
            tracker.onWriteStarted(AudioStream.Id.STREAM_VOICE_CALL, 8)
            tracker.onWriteFinished()

            tracker.hasRecentTarget(AudioStream.Id.STREAM_VOICE_CALL, 8) shouldBe true
            tracker.hasRecentTarget(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 8) shouldBe true
        }

        @Test
        fun `hasRecentTarget expires after TTL`() {
            tracker.onWriteStarted(AudioStream.Id.STREAM_MUSIC, 10)
            tracker.onWriteFinished()

            fakeTime += 2500
            tracker.hasRecentTarget(AudioStream.Id.STREAM_MUSIC, 10) shouldBe false
        }

        @Test
        fun `hasRecentTarget returns false for unrelated stream`() {
            tracker.onWriteStarted(AudioStream.Id.STREAM_MUSIC, 10)
            tracker.onWriteFinished()

            tracker.hasRecentTarget(AudioStream.Id.STREAM_RINGTONE, 10) shouldBe false
        }
    }

    @Nested
    inner class ActiveWrite {
        @Test
        fun `wasUs returns true for written stream during active write`() {
            tracker.onWriteStarted(AudioStream.Id.STREAM_MUSIC, 10)

            tracker.wasUs(AudioStream.Id.STREAM_MUSIC, 10) shouldBe true
        }

        @Test
        fun `wasUs returns true for mirrored peer during active write`() {
            tracker.onWriteStarted(AudioStream.Id.STREAM_VOICE_CALL, 8)

            tracker.wasUs(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 8) shouldBe true
        }

        @Test
        fun `wasUs returns false for unrelated stream during active write`() {
            tracker.onWriteStarted(AudioStream.Id.STREAM_MUSIC, 10)

            tracker.wasUs(AudioStream.Id.STREAM_RINGTONE, 10) shouldBe false
        }
    }

    @Nested
    inner class Basics {
        @Test
        fun `wasUs returns false for unknown stream`() {
            tracker.wasUs(AudioStream.Id.STREAM_MUSIC, 5) shouldBe false
        }

        @Test
        fun `wasUs returns false when volume does not match`() {
            tracker.onWriteStarted(AudioStream.Id.STREAM_MUSIC, 5)
            tracker.onWriteFinished()

            tracker.wasUs(AudioStream.Id.STREAM_MUSIC, 3) shouldBe false
        }
    }
}
