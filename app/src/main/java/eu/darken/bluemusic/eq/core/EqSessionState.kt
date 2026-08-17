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
    val closed: Boolean = false,
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
) {

    /** Sessions of the current listening generation that are still open. */
    val openSessions: List<EqSession>
        get() = sessions.values.filter { it.generation == generation && !it.closed }

    companion object {
        /** Ring buffer size for the diagnostic event log. */
        const val MAX_EVENTS = 200

        /** Per-generation cap on how many events of a spammable type are recorded. */
        const val MAX_RATE_CAPPED_EVENTS = 20
    }
}
