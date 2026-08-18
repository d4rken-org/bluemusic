package eu.darken.bluemusic.eq.core

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class EqSessionReducerTest : BaseTest() {

    private val reducer = EqSessionReducer()
    private val t0 = Instant.parse("2026-01-01T10:00:00Z")
    private fun at(seconds: Long): Instant = t0.plusSeconds(seconds)

    private fun listening(generation: Long = 1L): EqSessionState =
        reducer.onListeningStarted(EqSessionState(), t0, generation, "on")

    @Test
    fun `open creates a session`() {
        val state = reducer.onOpenBroadcast(listening(), t0, 1L, "com.spotify.music", 42)

        state.sessions.values.single() shouldBe EqSession(
            sessionId = 42,
            generation = 1L,
            openedAt = t0,
            packageName = "com.spotify.music",
        )
        state.events.last().type shouldBe EqEvent.Type.OPEN
    }

    @Test
    fun `open with non positive session id is malformed`() {
        val state = reducer.onOpenBroadcast(listening(), t0, 1L, "com.spotify.music", 0)

        state.sessions.shouldBeEmpty()
        state.events.last().type shouldBe EqEvent.Type.MALFORMED
    }

    @Test
    fun `open with negative session id is malformed`() {
        val state = reducer.onOpenBroadcast(listening(), t0, 1L, "com.spotify.music", -1)

        state.sessions.shouldBeEmpty()
        state.events.last().type shouldBe EqEvent.Type.MALFORMED
    }

    @Test
    fun `open with missing session id extra is malformed`() {
        val state = reducer.onOpenBroadcast(listening(), t0, 1L, "com.spotify.music", null)

        state.sessions.shouldBeEmpty()
        state.events.last().type shouldBe EqEvent.Type.MALFORMED
        state.events.last().sessionId shouldBe null
    }

    @Test
    fun `session identity is the session id, the package is only diagnostic`() {
        var state = reducer.onOpenBroadcast(listening(), t0, 1L, null, 42)
        state.sessions.values.single().packageName shouldBe null

        state = reducer.onOpenBroadcast(state, at(1), 1L, "com.spotify.music", 42)

        state.sessions.values.single().sessionId shouldBe 42
        state.sessions.values.single().packageName shouldBe "com.spotify.music"
    }

    @Test
    fun `duplicate open refreshes the existing session`() {
        var state = reducer.onOpenBroadcast(listening(), t0, 1L, "com.spotify.music", 42)
        state = reducer.onAttached(state, at(1), 42, "attached")
        state = reducer.onOpenBroadcast(state, at(2), 1L, "com.spotify.music", 42)

        state.sessions.values.single() shouldBe EqSession(
            sessionId = 42,
            generation = 1L,
            openedAt = at(2),
            packageName = "com.spotify.music",
            attached = true,
        )
        state.events.map { it.type } shouldBe listOf(
            EqEvent.Type.LISTENING,
            EqEvent.Type.OPEN,
            EqEvent.Type.ATTACH,
            EqEvent.Type.OPEN,
        )
    }

    @Test
    fun `close removes the session entirely`() {
        var state = reducer.onOpenBroadcast(listening(), t0, 1L, "com.spotify.music", 42)
        state = reducer.onAttached(state, at(1), 42, "attached")
        state = reducer.onControlChanged(state, at(2), 42, true)
        state = reducer.onCloseBroadcast(state, at(3), 1L, "com.spotify.music", 42)

        state.sessions.shouldBeEmpty()
        state.openSessions.shouldBeEmpty()
        state.events.last().type shouldBe EqEvent.Type.CLOSE
    }

    @Test
    fun `unmatched close only records an event`() {
        val state = reducer.onCloseBroadcast(listening(), t0, 1L, "com.spotify.music", 42)

        state.sessions.shouldBeEmpty()
        state.events.last().type shouldBe EqEvent.Type.CLOSE
    }

    @Test
    fun `close with invalid session id is malformed`() {
        val state = reducer.onCloseBroadcast(listening(), t0, 1L, "com.spotify.music", 0)

        state.sessions.shouldBeEmpty()
        state.events.last().type shouldBe EqEvent.Type.MALFORMED
    }

    @Test
    fun `reopen after close creates a fresh session row`() {
        var state = reducer.onOpenBroadcast(listening(), t0, 1L, "com.spotify.music", 42)
        state = reducer.onAttached(state, at(1), 42, "attached")
        state = reducer.onCloseBroadcast(state, at(2), 1L, "com.spotify.music", 42)
        state = reducer.onOpenBroadcast(state, at(3), 1L, "com.spotify.music", 42)

        state.sessions.values.single() shouldBe EqSession(
            sessionId = 42,
            generation = 1L,
            openedAt = at(3),
            packageName = "com.spotify.music",
            attached = false,
        )
        state.openSessions.map { it.sessionId } shouldBe listOf(42)
    }

    // region session bounds

    @Test
    fun `tracked sessions are capped and the cap is reported once per generation`() {
        var state = listening()
        repeat(EqSessionState.MAX_SESSIONS + 10) { i ->
            state = reducer.onOpenBroadcast(state, at(i.toLong()), 1L, "com.bad.app", i + 1)
        }

        state.sessions.size shouldBe EqSessionState.MAX_SESSIONS
        state.sessions.keys.maxOrNull() shouldBe EqSessionState.MAX_SESSIONS
        state.events.count { it.type == EqEvent.Type.SUPPRESSED && it.detail.contains("sessions this generation") } shouldBe 1
    }

    @Test
    fun `a session known before the cap still updates while the cap is reached`() {
        var state = listening()
        repeat(EqSessionState.MAX_SESSIONS) { i ->
            state = reducer.onOpenBroadcast(state, at(i.toLong()), 1L, "com.spotify.music", i + 1)
        }
        state = reducer.onOpenBroadcast(state, at(500), 1L, "com.spotify.music", 1)

        state.sessions.size shouldBe EqSessionState.MAX_SESSIONS
        state.sessions.getValue(1).openedAt shouldBe at(500)
    }

    @Test
    fun `closing sessions frees room under the cap again`() {
        var state = listening()
        repeat(EqSessionState.MAX_SESSIONS) { i ->
            state = reducer.onOpenBroadcast(state, at(i.toLong()), 1L, "com.spotify.music", i + 1)
        }
        state = reducer.onOpenBroadcast(state, at(100), 1L, "com.spotify.music", 999)
        state.sessions.containsKey(999) shouldBe false

        state = reducer.onCloseBroadcast(state, at(101), 1L, "com.spotify.music", 1)
        state = reducer.onOpenBroadcast(state, at(102), 1L, "com.spotify.music", 999)

        state.sessions.size shouldBe EqSessionState.MAX_SESSIONS
        state.sessions.containsKey(1) shouldBe false
        state.sessions.containsKey(999) shouldBe true
    }

    @Test
    fun `the cap notice resets with a new generation`() {
        var state = listening()
        repeat(EqSessionState.MAX_SESSIONS + 10) { i ->
            state = reducer.onOpenBroadcast(state, at(i.toLong()), 1L, "com.bad.app", i + 1)
        }
        state.sessionCapReported shouldBe true

        state = reducer.onListeningStarted(state, at(200), 2L, "on again")

        state.sessionCapReported shouldBe false
        state.sessions.shouldBeEmpty()
    }

    // endregion

    @Test
    fun `attach failure clears attachment state`() {
        var state = reducer.onOpenBroadcast(listening(), t0, 1L, "com.spotify.music", 42)
        state = reducer.onAttached(state, at(1), 42, "attached")
        state = reducer.onAttachFailed(state, at(2), 42, "boom")

        state.sessions.values.single().attached shouldBe false
        state.events.last().type shouldBe EqEvent.Type.ATTACH_FAILED
        state.events.last().detail shouldBe "boom"
    }

    @Test
    fun `detach clears attachment state`() {
        var state = reducer.onOpenBroadcast(listening(), t0, 1L, "com.spotify.music", 42)
        state = reducer.onAttached(state, at(1), 42, "attached")
        state = reducer.onControlChanged(state, at(2), 42, true)
        state = reducer.onDetached(state, at(3), 42)

        state.sessions.values.single().attached shouldBe false
        state.sessions.values.single().hasControl shouldBe null
        state.events.last().type shouldBe EqEvent.Type.DETACH
    }

    @Test
    fun `control change updates the session`() {
        var state = reducer.onOpenBroadcast(listening(), t0, 1L, "com.spotify.music", 42)
        state = reducer.onAttached(state, at(1), 42, "attached")
        state = reducer.onControlChanged(state, at(2), 42, false)

        state.sessions.values.single().hasControl shouldBe false
        state.events.last().detail shouldBe "hasControl=false"
    }

    // region generation scoping

    @Test
    fun `starting to listen drops sessions of the previous generation`() {
        var state = reducer.onOpenBroadcast(listening(1L), t0, 1L, "com.spotify.music", 42)
        state.sessions.size shouldBe 1

        state = reducer.onListeningStarted(state, at(1), 2L, "on again")

        state.sessions.shouldBeEmpty()
        state.generation shouldBe 2L
    }

    @Test
    fun `stopping listening drops all sessions`() {
        var state = reducer.onOpenBroadcast(listening(), t0, 1L, "com.spotify.music", 42)
        state = reducer.onListeningStopped(state, at(1), 2L, "off")

        state.listening shouldBe false
        state.generation shouldBe 2L
        state.sessions.shouldBeEmpty()
        state.openSessions.shouldBeEmpty()
    }

    @Test
    fun `a broadcast arriving after stop does not repopulate state`() {
        var state = reducer.onOpenBroadcast(listening(1L), t0, 1L, "com.spotify.music", 42)
        state = reducer.onListeningStopped(state, at(1), 2L, "off")

        // The receiver was still delivering when it was unregistered: same generation it registered with.
        state = reducer.onOpenBroadcast(state, at(2), 1L, "com.spotify.music", 42)
        state = reducer.onCloseBroadcast(state, at(3), 1L, "com.spotify.music", 7)

        state.listening shouldBe false
        state.sessions.shouldBeEmpty()
        state.openSessions.shouldBeEmpty()
        state.events.map { it.detail }.takeLast(2) shouldBe listOf("Not listening", "Not listening")
    }

    @Test
    fun `broadcasts are rejected while not listening even on a matching generation`() {
        val stopped = EqSessionState(listening = false, generation = 5L)

        val state = reducer.onOpenBroadcast(stopped, t0, 5L, "com.spotify.music", 42)

        state.sessions.shouldBeEmpty()
        state.events.last().type shouldBe EqEvent.Type.OPEN
        state.events.last().detail shouldBe "Not listening"
    }

    @Test
    fun `open from a stale generation is ignored`() {
        val state = reducer.onOpenBroadcast(listening(2L), t0, 1L, "com.spotify.music", 42)

        state.sessions.shouldBeEmpty()
        state.openSessions.shouldBeEmpty()
        state.events.last().detail shouldContain "stale generation 1"
    }

    @Test
    fun `close from a stale generation does not touch current sessions`() {
        var state = reducer.onOpenBroadcast(listening(2L), t0, 2L, "com.spotify.music", 42)
        state = reducer.onCloseBroadcast(state, at(1), 1L, "com.spotify.music", 42)

        state.sessions.keys shouldBe setOf(42)
        state.openSessions.map { it.sessionId } shouldBe listOf(42)
    }

    @Test
    fun `listening toggle is tracked`() {
        var state = reducer.onListeningStarted(EqSessionState(), t0, 1L, "on")
        state.listening shouldBe true

        state = reducer.onListeningStopped(state, at(1), 2L, "off")
        state.listening shouldBe false
        state.events.map { it.type } shouldBe listOf(EqEvent.Type.LISTENING, EqEvent.Type.LISTENING)
    }

    // endregion

    // region event log limits

    @Test
    fun `event log is a bounded ring buffer`() {
        var state = listening()
        repeat(EqSessionState.MAX_EVENTS + 50) { i ->
            state = reducer.onControlChanged(state, at(i.toLong()), 42, i % 2 == 0)
        }

        state.events.size shouldBe EqSessionState.MAX_EVENTS
        state.events.last().time shouldBe at((EqSessionState.MAX_EVENTS + 50 - 1).toLong())
    }

    @Test
    fun `malformed events are rate capped per generation`() {
        var state = listening()
        repeat(50) { i -> state = reducer.onOpenBroadcast(state, at(i.toLong()), 1L, "com.bad.app", 0) }

        val malformed = state.events.count { it.type == EqEvent.Type.MALFORMED }
        malformed shouldBe EqSessionState.MAX_RATE_CAPPED_EVENTS
        state.events.count { it.type == EqEvent.Type.SUPPRESSED } shouldBe 1
        state.malformedCount shouldBe 50
    }

    @Test
    fun `open events are rate capped per generation but sessions still track`() {
        var state = listening()
        repeat(50) { i -> state = reducer.onOpenBroadcast(state, at(i.toLong()), 1L, "com.spotify.music", i + 1) }

        state.sessions.size shouldBe 50
        state.events.count { it.type == EqEvent.Type.OPEN } shouldBe EqSessionState.MAX_RATE_CAPPED_EVENTS
        state.events.count { it.type == EqEvent.Type.SUPPRESSED } shouldBe 1
    }

    @Test
    fun `rate cap resets with a new generation`() {
        var state = listening()
        repeat(50) { i -> state = reducer.onOpenBroadcast(state, at(i.toLong()), 1L, "com.bad.app", 0) }

        state = reducer.onListeningStarted(state, at(60), 2L, "on again")
        state = reducer.onOpenBroadcast(state, at(61), 2L, "com.bad.app", 0)

        state.malformedCount shouldBe 1
        state.events.last().type shouldBe EqEvent.Type.MALFORMED
    }

    // endregion

    @Test
    fun `clear resets sessions and events but keeps listening`() {
        var state = reducer.onOpenBroadcast(listening(), at(1), 1L, "com.spotify.music", 42)
        state = reducer.onAttached(state, at(2), 42, "attached")

        val cleared = reducer.clear(state, at(3))

        cleared.listening shouldBe true
        cleared.sessions.shouldBeEmpty()
        cleared.events.single() shouldBe EqEvent(time = at(3), type = EqEvent.Type.CLEARED)
    }
}
