package eu.darken.bluemusic.monitor.core.modules.volume

import eu.darken.bluemusic.monitor.core.audio.AudioStream
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class VolumeObservationGateTest : BaseTest() {

    private lateinit var gate: VolumeObservationGate

    @BeforeEach
    fun setup() {
        gate = VolumeObservationGate()
    }

    @Test
    fun `single suppress makes stream suppressed`() {
        val token = gate.suppress(AudioStream.Id.STREAM_MUSIC)
        gate.isSuppressed(AudioStream.Id.STREAM_MUSIC) shouldBe true
        gate.unsuppress(token)
        gate.isSuppressed(AudioStream.Id.STREAM_MUSIC) shouldBe false
    }

    @Test
    fun `two concurrent suppressors - first unsuppress does not expose stream`() {
        val token1 = gate.suppress(AudioStream.Id.STREAM_MUSIC)
        val token2 = gate.suppress(AudioStream.Id.STREAM_MUSIC)

        gate.isSuppressed(AudioStream.Id.STREAM_MUSIC) shouldBe true

        gate.unsuppress(token1)
        gate.isSuppressed(AudioStream.Id.STREAM_MUSIC) shouldBe true

        gate.unsuppress(token2)
        gate.isSuppressed(AudioStream.Id.STREAM_MUSIC) shouldBe false
    }

    @Test
    fun `suppress includes mirrored peer for VOICE_CALL`() {
        val token = gate.suppress(AudioStream.Id.STREAM_VOICE_CALL)

        gate.isSuppressed(AudioStream.Id.STREAM_VOICE_CALL) shouldBe true
        gate.isSuppressed(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE) shouldBe true

        gate.unsuppress(token)
        gate.isSuppressed(AudioStream.Id.STREAM_VOICE_CALL) shouldBe false
        gate.isSuppressed(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE) shouldBe false
    }

    @Test
    fun `suppress includes mirrored peer for BT_HANDSFREE`() {
        val token = gate.suppress(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE)

        gate.isSuppressed(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE) shouldBe true
        gate.isSuppressed(AudioStream.Id.STREAM_VOICE_CALL) shouldBe true

        gate.unsuppress(token)
        gate.isSuppressed(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE) shouldBe false
        gate.isSuppressed(AudioStream.Id.STREAM_VOICE_CALL) shouldBe false
    }

    @Test
    fun `MUSIC suppress does not affect other streams`() {
        val token = gate.suppress(AudioStream.Id.STREAM_MUSIC)

        gate.isSuppressed(AudioStream.Id.STREAM_RINGTONE) shouldBe false
        gate.isSuppressed(AudioStream.Id.STREAM_ALARM) shouldBe false
        gate.isSuppressed(AudioStream.Id.STREAM_VOICE_CALL) shouldBe false

        gate.unsuppress(token)
    }

    @Test
    fun `concurrent suppressors on mirrored pairs ref-counted independently`() {
        val token1 = gate.suppress(AudioStream.Id.STREAM_VOICE_CALL)
        val token2 = gate.suppress(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE)

        // Both directions suppressed with ref count 2 each
        gate.isSuppressed(AudioStream.Id.STREAM_VOICE_CALL) shouldBe true
        gate.isSuppressed(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE) shouldBe true

        gate.unsuppress(token1)
        // token2 still holds both
        gate.isSuppressed(AudioStream.Id.STREAM_VOICE_CALL) shouldBe true
        gate.isSuppressed(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE) shouldBe true

        gate.unsuppress(token2)
        gate.isSuppressed(AudioStream.Id.STREAM_VOICE_CALL) shouldBe false
        gate.isSuppressed(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE) shouldBe false
    }

    @Test
    fun `unsuppressed stream is not suppressed`() {
        gate.isSuppressed(AudioStream.Id.STREAM_MUSIC) shouldBe false
    }
}
