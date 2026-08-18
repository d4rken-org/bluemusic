package eu.darken.bluemusic.eq.core

import java.time.Instant

data class EqEvent(
    val time: Instant,
    val type: Type,
    val packageName: String? = null,
    val sessionId: Int? = null,
    val detail: String = "",
) {
    enum class Type {
        OPEN,
        CLOSE,
        MALFORMED,
        ATTACH,
        ATTACH_FAILED,
        DETACH,
        CONTROL_CHANGED,
        LISTENING,
        CLEARED,
        SUPPRESSED,
        ;
    }
}

/**
 * A validated session edge, in the order the broadcasts arrived.
 *
 * The coordinator acts on these instead of diffing a snapshot of open session ids: a CLOSE followed
 * immediately by an OPEN for the same id is two edges, while both snapshots look identical.
 */
data class EqTransition(
    val type: Type,
    val sessionId: Int,
    val generation: Long,
) {
    enum class Type {
        OPEN,
        CLOSE,
        ;
    }
}

/** Thrown into the transition stream when an edge could not be buffered and was lost. */
class EqTransitionOverflow(message: String) : IllegalStateException(message)

/**
 * An audio effect control session another app told us about.
 *
 * Identity is [sessionId] alone: `EXTRA_PACKAGE_NAME` is diagnostic only, some apps don't send it
 * and the framework keys the shared effect engine by session.
 *
 * [generation] is the listening generation this session was seen in. Sessions from an older
 * generation are stale and never attached to again.
 */
data class EqSession(
    val sessionId: Int,
    val generation: Long,
    val openedAt: Instant,
    val packageName: String? = null,
    val attached: Boolean = false,
    val hasControl: Boolean? = null,
)

data class EqSessionState(
    val listening: Boolean = false,
    val generation: Long = 0L,
    val sessions: Map<Int, EqSession> = emptyMap(),
    val events: List<EqEvent> = emptyList(),
    val malformedCount: Int = 0,
    val openCount: Int = 0,
    /** Whether the tracked-session cap was already reported in this generation. */
    val sessionCapReported: Boolean = false,
) {

    /** Sessions of the current listening generation. Closed ones are removed, not kept around. */
    val openSessions: List<EqSession>
        get() = sessions.values.filter { it.generation == generation }

    companion object {
        /** Ring buffer size for the diagnostic event log. */
        const val MAX_EVENTS = 200

        /** Per-generation cap on how many events of a spammable type are recorded. */
        const val MAX_RATE_CAPPED_EVENTS = 20

        /** Upper bound on tracked sessions, a misbehaving app can spam OPEN broadcasts. */
        const val MAX_SESSIONS = 64
    }
}
