package eu.darken.bluemusic.eqspike.core

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class EqSpikeReducerTest : BaseTest() {

    private val reducer = EqSpikeReducer()
    private val t0 = Instant.parse("2026-01-01T10:00:00Z")
    private fun at(seconds: Long): Instant = t0.plusSeconds(seconds)

    @Test
    fun `open creates a session`() {
        val state = reducer.onOpenBroadcast(EqSpikeState(), t0, "com.spotify.music", 42)

        state.sessions.single() shouldBe SpikeSession(
            packageName = "com.spotify.music",
            sessionId = 42,
            openedAt = t0,
        )
        state.events.single().type shouldBe SpikeEvent.Type.OPEN
    }

    @Test
    fun `open with non positive session id is malformed`() {
        val state = reducer.onOpenBroadcast(EqSpikeState(), t0, "com.spotify.music", 0)

        state.sessions.shouldBeEmpty()
        state.events.single().type shouldBe SpikeEvent.Type.MALFORMED
    }

    @Test
    fun `open with negative session id is malformed`() {
        val state = reducer.onOpenBroadcast(EqSpikeState(), t0, "com.spotify.music", -1)

        state.sessions.shouldBeEmpty()
        state.events.single().type shouldBe SpikeEvent.Type.MALFORMED
    }

    @Test
    fun `open with missing session id extra is malformed`() {
        val state = reducer.onOpenBroadcast(EqSpikeState(), t0, "com.spotify.music", null)

        state.sessions.shouldBeEmpty()
        state.events.single().type shouldBe SpikeEvent.Type.MALFORMED
        state.events.single().sessionId shouldBe null
    }

    @Test
    fun `open with null package is malformed`() {
        val state = reducer.onOpenBroadcast(EqSpikeState(), t0, null, 42)

        state.sessions.shouldBeEmpty()
        state.events.single().type shouldBe SpikeEvent.Type.MALFORMED
    }

    @Test
    fun `open with blank package is malformed`() {
        val state = reducer.onOpenBroadcast(EqSpikeState(), t0, "   ", 42)

        state.sessions.shouldBeEmpty()
        state.events.single().type shouldBe SpikeEvent.Type.MALFORMED
    }

    @Test
    fun `same session id from different packages are separate sessions`() {
        var state = reducer.onOpenBroadcast(EqSpikeState(), t0, "com.spotify.music", 42)
        state = reducer.onOpenBroadcast(state, at(1), "com.soundcloud.android", 42)

        state.sessions.map { it.packageName } shouldBe listOf("com.spotify.music", "com.soundcloud.android")
    }

    @Test
    fun `duplicate open refreshes the existing session`() {
        var state = reducer.onOpenBroadcast(EqSpikeState(), t0, "com.spotify.music", 42)
        state = reducer.onAttached(state, at(1), "com.spotify.music", 42, "attached")
        state = reducer.onOpenBroadcast(state, at(2), "com.spotify.music", 42)

        state.sessions.single() shouldBe SpikeSession(
            packageName = "com.spotify.music",
            sessionId = 42,
            openedAt = at(2),
            closed = false,
            attached = true,
        )
        state.events.map { it.type } shouldBe listOf(
            SpikeEvent.Type.OPEN,
            SpikeEvent.Type.ATTACH,
            SpikeEvent.Type.OPEN,
        )
    }

    @Test
    fun `close marks the session closed and drops attachment state`() {
        var state = reducer.onOpenBroadcast(EqSpikeState(), t0, "com.spotify.music", 42)
        state = reducer.onAttached(state, at(1), "com.spotify.music", 42, "attached")
        state = reducer.onControlChanged(state, at(2), "com.spotify.music", 42, true)
        state = reducer.onCloseBroadcast(state, at(3), "com.spotify.music", 42)

        state.sessions.single() shouldBe SpikeSession(
            packageName = "com.spotify.music",
            sessionId = 42,
            openedAt = t0,
            closed = true,
            attached = false,
            hasControl = null,
        )
        state.events.last().type shouldBe SpikeEvent.Type.CLOSE
    }

    @Test
    fun `unmatched close only records an event`() {
        val state = reducer.onCloseBroadcast(EqSpikeState(), t0, "com.spotify.music", 42)

        state.sessions.shouldBeEmpty()
        state.events.single().type shouldBe SpikeEvent.Type.CLOSE
    }

    @Test
    fun `close with invalid session id is malformed`() {
        val state = reducer.onCloseBroadcast(EqSpikeState(), t0, "com.spotify.music", 0)

        state.sessions.shouldBeEmpty()
        state.events.single().type shouldBe SpikeEvent.Type.MALFORMED
    }

    @Test
    fun `reopen after close revives the same session row`() {
        var state = reducer.onOpenBroadcast(EqSpikeState(), t0, "com.spotify.music", 42)
        state = reducer.onCloseBroadcast(state, at(1), "com.spotify.music", 42)
        state = reducer.onOpenBroadcast(state, at(2), "com.spotify.music", 42)

        state.sessions.single() shouldBe SpikeSession(
            packageName = "com.spotify.music",
            sessionId = 42,
            openedAt = at(2),
            closed = false,
        )
    }

    @Test
    fun `attach failure clears attachment state`() {
        var state = reducer.onOpenBroadcast(EqSpikeState(), t0, "com.spotify.music", 42)
        state = reducer.onAttached(state, at(1), "com.spotify.music", 42, "attached")
        state = reducer.onAttachFailed(state, at(2), "com.spotify.music", 42, "boom")

        state.sessions.single().attached shouldBe false
        state.events.last().type shouldBe SpikeEvent.Type.ATTACH_FAILED
        state.events.last().detail shouldBe "boom"
    }

    @Test
    fun `detach clears attachment state`() {
        var state = reducer.onOpenBroadcast(EqSpikeState(), t0, "com.spotify.music", 42)
        state = reducer.onAttached(state, at(1), "com.spotify.music", 42, "attached")
        state = reducer.onControlChanged(state, at(2), "com.spotify.music", 42, true)
        state = reducer.onDetached(state, at(3), "com.spotify.music", 42)

        state.sessions.single().attached shouldBe false
        state.sessions.single().hasControl shouldBe null
        state.events.last().type shouldBe SpikeEvent.Type.DETACH
    }

    @Test
    fun `control change updates the session`() {
        var state = reducer.onOpenBroadcast(EqSpikeState(), t0, "com.spotify.music", 42)
        state = reducer.onAttached(state, at(1), "com.spotify.music", 42, "attached")
        state = reducer.onControlChanged(state, at(2), "com.spotify.music", 42, false)

        state.sessions.single().hasControl shouldBe false
        state.events.last().detail shouldBe "hasControl=false"
    }

    @Test
    fun `listening toggle is tracked`() {
        var state = reducer.onListeningChanged(EqSpikeState(), t0, listening = true, detail = "on")
        state.listening shouldBe true

        state = reducer.onListeningChanged(state, at(1), listening = false, detail = "off")
        state.listening shouldBe false
        state.events.map { it.type } shouldBe listOf(SpikeEvent.Type.LISTENING, SpikeEvent.Type.LISTENING)
    }

    @Test
    fun `openEventsAfter only returns opens strictly after the cutoff`() {
        var state = reducer.onOpenBroadcast(EqSpikeState(), t0, "com.spotify.music", 42)
        state = reducer.onCloseBroadcast(state, at(10), "com.spotify.music", 42)
        state = reducer.onOpenBroadcast(state, at(20), "com.soundcloud.android", 7)

        reducer.openEventsAfter(state, t0).map { it.packageName } shouldBe listOf("com.soundcloud.android")
        reducer.openEventsAfter(state, at(20)).shouldBeEmpty()
        reducer.openEventsAfter(state, t0.minusSeconds(1)).map { it.sessionId } shouldBe listOf(42, 7)
    }

    @Test
    fun `clear resets sessions and events but keeps listening`() {
        var state = reducer.onListeningChanged(EqSpikeState(), t0, listening = true, detail = "on")
        state = reducer.onOpenBroadcast(state, at(1), "com.spotify.music", 42)
        state = reducer.onAttached(state, at(2), "com.spotify.music", 42, "attached")

        val cleared = reducer.clear(state, at(3))

        cleared.listening shouldBe true
        cleared.sessions.shouldBeEmpty()
        cleared.events.single() shouldBe SpikeEvent(time = at(3), type = SpikeEvent.Type.CLEARED)
    }
}
