package eu.darken.bluemusic.eqspike.core

import java.time.Instant

/**
 * Pure state transitions for the audio-effect-control-session spike.
 * No Android framework access, all timestamps are passed in.
 */
class EqSpikeReducer {

    fun onListeningChanged(
        state: EqSpikeState,
        now: Instant,
        listening: Boolean,
        detail: String,
    ): EqSpikeState = state
        .copy(listening = listening)
        .plusEvent(SpikeEvent(time = now, type = SpikeEvent.Type.LISTENING, detail = detail))

    fun onOpenBroadcast(
        state: EqSpikeState,
        now: Instant,
        packageName: String?,
        sessionId: Int?,
    ): EqSpikeState {
        val valid = when (val check = validate(packageName, sessionId)) {
            is Validation.Malformed -> return state.plusMalformed(now, packageName, sessionId, check.reason)
            is Validation.Valid -> check
        }
        val (pkg, session) = valid

        val existing = state.sessions.find { it.packageName == pkg && it.sessionId == session }
        val sessions = when {
            // Last event wins: some apps re-broadcast OPEN for a session we already know about.
            existing != null -> state.sessions.map {
                if (it === existing) it.copy(openedAt = now, closed = false) else it
            }

            else -> state.sessions + SpikeSession(
                packageName = pkg,
                sessionId = session,
                openedAt = now,
            )
        }

        return state
            .copy(sessions = sessions)
            .plusEvent(
                SpikeEvent(
                    time = now,
                    type = SpikeEvent.Type.OPEN,
                    packageName = pkg,
                    sessionId = session,
                    detail = if (existing != null) "Reopened" else "New session",
                )
            )
    }

    fun onCloseBroadcast(
        state: EqSpikeState,
        now: Instant,
        packageName: String?,
        sessionId: Int?,
    ): EqSpikeState {
        val valid = when (val check = validate(packageName, sessionId)) {
            is Validation.Malformed -> return state.plusMalformed(now, packageName, sessionId, check.reason)
            is Validation.Valid -> check
        }
        val (pkg, session) = valid

        val existing = state.sessions.find { it.packageName == pkg && it.sessionId == session }
        val sessions = when (existing) {
            null -> state.sessions
            else -> state.sessions.map {
                if (it === existing) it.copy(closed = true, attached = false, hasControl = null) else it
            }
        }

        return state
            .copy(sessions = sessions)
            .plusEvent(
                SpikeEvent(
                    time = now,
                    type = SpikeEvent.Type.CLOSE,
                    packageName = pkg,
                    sessionId = session,
                    detail = if (existing != null) "Session closed" else "Unmatched close",
                )
            )
    }

    fun onAttached(
        state: EqSpikeState,
        now: Instant,
        packageName: String,
        sessionId: Int,
        detail: String,
    ): EqSpikeState = state
        .copy(sessions = state.updateSession(packageName, sessionId) { it.copy(attached = true) })
        .plusEvent(
            SpikeEvent(
                time = now,
                type = SpikeEvent.Type.ATTACH,
                packageName = packageName,
                sessionId = sessionId,
                detail = detail,
            )
        )

    fun onAttachFailed(
        state: EqSpikeState,
        now: Instant,
        packageName: String,
        sessionId: Int,
        detail: String,
    ): EqSpikeState = state
        .copy(sessions = state.updateSession(packageName, sessionId) { it.copy(attached = false, hasControl = null) })
        .plusEvent(
            SpikeEvent(
                time = now,
                type = SpikeEvent.Type.ATTACH_FAILED,
                packageName = packageName,
                sessionId = sessionId,
                detail = detail,
            )
        )

    fun onDetached(
        state: EqSpikeState,
        now: Instant,
        packageName: String,
        sessionId: Int,
        detail: String = "",
    ): EqSpikeState = state
        .copy(sessions = state.updateSession(packageName, sessionId) { it.copy(attached = false, hasControl = null) })
        .plusEvent(
            SpikeEvent(
                time = now,
                type = SpikeEvent.Type.DETACH,
                packageName = packageName,
                sessionId = sessionId,
                detail = detail,
            )
        )

    fun onControlChanged(
        state: EqSpikeState,
        now: Instant,
        packageName: String,
        sessionId: Int,
        hasControl: Boolean,
    ): EqSpikeState = state
        .copy(sessions = state.updateSession(packageName, sessionId) { it.copy(hasControl = hasControl) })
        .plusEvent(
            SpikeEvent(
                time = now,
                type = SpikeEvent.Type.CONTROL_CHANGED,
                packageName = packageName,
                sessionId = sessionId,
                detail = "hasControl=$hasControl",
            )
        )

    fun clear(state: EqSpikeState, now: Instant): EqSpikeState = state.copy(
        sessions = emptyList(),
        events = listOf(SpikeEvent(time = now, type = SpikeEvent.Type.CLEARED)),
    )

    fun openEventsAfter(state: EqSpikeState, cutoff: Instant): List<SpikeEvent> = state.events
        .filter { it.type == SpikeEvent.Type.OPEN && it.time.isAfter(cutoff) }

    private sealed interface Validation {
        data class Valid(val packageName: String, val sessionId: Int) : Validation
        data class Malformed(val reason: String) : Validation
    }

    /**
     * A missing EXTRA_AUDIO_SESSION would default to 0, which targets the deprecated global output
     * mix. Attaching there affects all audio and would fake a positive result for this spike, so
     * anything that isn't a real session id is recorded as malformed instead.
     */
    private fun validate(packageName: String?, sessionId: Int?): Validation = when {
        packageName.isNullOrBlank() -> Validation.Malformed("Missing package name (sessionId=$sessionId)")
        sessionId == null -> Validation.Malformed("Missing session id extra")
        sessionId <= 0 -> Validation.Malformed("Invalid session id: $sessionId")
        else -> Validation.Valid(packageName, sessionId)
    }

    private fun EqSpikeState.plusMalformed(
        now: Instant,
        packageName: String?,
        sessionId: Int?,
        reason: String,
    ): EqSpikeState = plusEvent(
        SpikeEvent(
            time = now,
            type = SpikeEvent.Type.MALFORMED,
            packageName = packageName,
            sessionId = sessionId,
            detail = reason,
        )
    )

    private fun EqSpikeState.plusEvent(event: SpikeEvent): EqSpikeState = copy(events = events + event)

    private fun EqSpikeState.updateSession(
        packageName: String,
        sessionId: Int,
        action: (SpikeSession) -> SpikeSession,
    ): List<SpikeSession> = sessions.map {
        if (it.packageName == packageName && it.sessionId == sessionId) action(it) else it
    }
}
