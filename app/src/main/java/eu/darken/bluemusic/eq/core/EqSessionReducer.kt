package eu.darken.bluemusic.eq.core

import eu.darken.bluemusic.eq.core.EqSessionState.Companion.MAX_EVENTS
import eu.darken.bluemusic.eq.core.EqSessionState.Companion.MAX_RATE_CAPPED_EVENTS
import eu.darken.bluemusic.eq.core.EqSessionState.Companion.MAX_SESSIONS
import java.time.Instant

/**
 * Pure state transitions for audio effect control session tracking.
 * No Android framework access, all timestamps and generations are passed in.
 */
class EqSessionReducer {

    /**
     * Starts a new listening generation. Sessions from the previous generation are dropped, they
     * belong to a receiver registration that is no longer active.
     */
    fun onListeningStarted(
        state: EqSessionState,
        now: Instant,
        generation: Long,
        detail: String,
    ): EqSessionState = state
        .copy(
            listening = true,
            generation = generation,
            sessions = emptyMap(),
            malformedCount = 0,
            openCount = 0,
            sessionCapReported = false,
        )
        .plusEvent(EqEvent(time = now, type = EqEvent.Type.LISTENING, detail = detail))

    /**
     * Ends the current listening generation. [generation] is the invalidated one the caller moved to
     * before it unregistered, so a broadcast racing the unregister can only arrive stale.
     */
    fun onListeningStopped(
        state: EqSessionState,
        now: Instant,
        generation: Long,
        detail: String,
    ): EqSessionState = state
        .copy(listening = false, generation = generation, sessions = emptyMap())
        .plusEvent(EqEvent(time = now, type = EqEvent.Type.LISTENING, detail = detail))

    fun onOpenBroadcast(
        state: EqSessionState,
        now: Instant,
        generation: Long,
        packageName: String?,
        sessionId: Int?,
    ): EqSessionState {
        val session = when (val check = validate(sessionId)) {
            is Validation.Malformed -> return state.plusMalformed(now, packageName, sessionId, check.reason)
            is Validation.Valid -> check.sessionId
        }
        if (!state.listening) return state.plusIgnored(now, EqEvent.Type.OPEN, packageName, session, "Not listening")
        if (generation != state.generation) return state.plusStale(now, EqEvent.Type.OPEN, packageName, session, generation)

        val existing = state.sessions[session]
        if (existing == null && state.sessions.size >= MAX_SESSIONS) {
            return state.plusSessionCapped(now, packageName, session)
        }

        val updated = when {
            // Last event wins: some apps re-broadcast OPEN for a session we already know about.
            existing != null -> existing.copy(
                openedAt = now,
                packageName = packageName ?: existing.packageName,
                generation = generation,
            )

            else -> EqSession(
                sessionId = session,
                generation = generation,
                openedAt = now,
                packageName = packageName,
            )
        }

        return state
            .copy(sessions = state.sessions + (session to updated), openCount = state.openCount + 1)
            .plusRateCapped(
                count = state.openCount,
                now = now,
                event = EqEvent(
                    time = now,
                    type = EqEvent.Type.OPEN,
                    packageName = packageName,
                    sessionId = session,
                    detail = if (existing != null) "Reopened" else "New session",
                ),
            )
    }

    fun onCloseBroadcast(
        state: EqSessionState,
        now: Instant,
        generation: Long,
        packageName: String?,
        sessionId: Int?,
    ): EqSessionState {
        val session = when (val check = validate(sessionId)) {
            is Validation.Malformed -> return state.plusMalformed(now, packageName, sessionId, check.reason)
            is Validation.Valid -> check.sessionId
        }
        if (!state.listening) return state.plusIgnored(now, EqEvent.Type.CLOSE, packageName, session, "Not listening")
        if (generation != state.generation) {
            return state.plusStale(now, EqEvent.Type.CLOSE, packageName, session, generation)
        }

        val existing = state.sessions[session]
        // Dropped, not flagged: a closed session is gone, and keeping rows would grow without bound.
        val sessions = when (existing) {
            null -> state.sessions
            else -> state.sessions - session
        }

        return state
            .copy(sessions = sessions)
            .plusEvent(
                EqEvent(
                    time = now,
                    type = EqEvent.Type.CLOSE,
                    packageName = packageName,
                    sessionId = session,
                    detail = if (existing != null) "Session closed" else "Unmatched close",
                )
            )
    }

    fun onAttached(
        state: EqSessionState,
        now: Instant,
        sessionId: Int,
        detail: String,
    ): EqSessionState = state
        .updateSession(sessionId) { it.copy(attached = true) }
        .plusEvent(EqEvent(time = now, type = EqEvent.Type.ATTACH, sessionId = sessionId, detail = detail))

    fun onAttachFailed(
        state: EqSessionState,
        now: Instant,
        sessionId: Int,
        detail: String,
    ): EqSessionState = state
        .updateSession(sessionId) { it.copy(attached = false, hasControl = null) }
        .plusEvent(EqEvent(time = now, type = EqEvent.Type.ATTACH_FAILED, sessionId = sessionId, detail = detail))

    fun onDetached(
        state: EqSessionState,
        now: Instant,
        sessionId: Int,
        detail: String = "",
    ): EqSessionState = state
        .updateSession(sessionId) { it.copy(attached = false, hasControl = null) }
        .plusEvent(EqEvent(time = now, type = EqEvent.Type.DETACH, sessionId = sessionId, detail = detail))

    fun onControlChanged(
        state: EqSessionState,
        now: Instant,
        sessionId: Int,
        hasControl: Boolean,
    ): EqSessionState = state
        .updateSession(sessionId) { it.copy(hasControl = hasControl) }
        .plusEvent(
            EqEvent(
                time = now,
                type = EqEvent.Type.CONTROL_CHANGED,
                sessionId = sessionId,
                detail = "hasControl=$hasControl",
            )
        )

    fun clear(state: EqSessionState, now: Instant): EqSessionState = state.copy(
        sessions = emptyMap(),
        events = listOf(EqEvent(time = now, type = EqEvent.Type.CLEARED)),
        malformedCount = 0,
        openCount = 0,
        sessionCapReported = false,
    )

    private sealed interface Validation {
        data class Valid(val sessionId: Int) : Validation
        data class Malformed(val reason: String) : Validation
    }

    /**
     * A missing EXTRA_AUDIO_SESSION would default to 0, which targets the deprecated global output
     * mix. Attaching there affects all audio on the device, so anything that isn't a real session
     * id is recorded as malformed instead.
     */
    private fun validate(sessionId: Int?): Validation = when {
        sessionId == null -> Validation.Malformed("Missing session id extra")
        sessionId <= 0 -> Validation.Malformed("Invalid session id: $sessionId")
        else -> Validation.Valid(sessionId)
    }

    private fun EqSessionState.plusMalformed(
        now: Instant,
        packageName: String?,
        sessionId: Int?,
        reason: String,
    ): EqSessionState = copy(malformedCount = malformedCount + 1).plusRateCapped(
        count = malformedCount,
        now = now,
        event = EqEvent(
            time = now,
            type = EqEvent.Type.MALFORMED,
            packageName = packageName,
            sessionId = sessionId,
            detail = reason,
        ),
    )

    private fun EqSessionState.plusStale(
        now: Instant,
        type: EqEvent.Type,
        packageName: String?,
        sessionId: Int,
        generation: Long,
    ): EqSessionState = plusIgnored(
        now = now,
        type = type,
        packageName = packageName,
        sessionId = sessionId,
        reason = "Ignored, stale generation $generation (current ${this.generation})",
    )

    private fun EqSessionState.plusIgnored(
        now: Instant,
        type: EqEvent.Type,
        packageName: String?,
        sessionId: Int,
        reason: String,
    ): EqSessionState = plusEvent(
        EqEvent(
            time = now,
            type = type,
            packageName = packageName,
            sessionId = sessionId,
            detail = reason,
        )
    )

    /**
     * Rejects a new session once [MAX_SESSIONS] are tracked and records one notice per generation,
     * so an app spamming OPEN for fresh session ids can't grow the map without bound.
     */
    private fun EqSessionState.plusSessionCapped(
        now: Instant,
        packageName: String?,
        sessionId: Int,
    ): EqSessionState = when {
        sessionCapReported -> this
        else -> copy(sessionCapReported = true).plusEvent(
            EqEvent(
                time = now,
                type = EqEvent.Type.SUPPRESSED,
                packageName = packageName,
                sessionId = sessionId,
                detail = "More than $MAX_SESSIONS sessions this generation, ignoring further new ones",
            )
        )
    }

    /**
     * Records [event] until [MAX_RATE_CAPPED_EVENTS] of its kind have been seen in this generation,
     * then records a single suppression notice and drops the rest. State transitions are unaffected,
     * only the diagnostic log is capped.
     */
    private fun EqSessionState.plusRateCapped(
        count: Int,
        now: Instant,
        event: EqEvent,
    ): EqSessionState = when {
        count < MAX_RATE_CAPPED_EVENTS -> plusEvent(event)
        count == MAX_RATE_CAPPED_EVENTS -> plusEvent(
            EqEvent(
                time = now,
                type = EqEvent.Type.SUPPRESSED,
                detail = "More than $MAX_RATE_CAPPED_EVENTS ${event.type} events this generation, dropping further ones",
            )
        )

        else -> this
    }

    private fun EqSessionState.plusEvent(event: EqEvent): EqSessionState = copy(
        events = (events + event).let { if (it.size > MAX_EVENTS) it.takeLast(MAX_EVENTS) else it }
    )

    private fun EqSessionState.updateSession(
        sessionId: Int,
        action: (EqSession) -> EqSession,
    ): EqSessionState {
        val existing = sessions[sessionId] ?: return this
        return copy(sessions = sessions + (sessionId to action(existing)))
    }
}
