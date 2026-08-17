package eu.darken.bluemusic.eqspike.core

import java.time.Instant

data class SpikeEvent(
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
        ;
    }
}

data class SpikeSession(
    val packageName: String,
    val sessionId: Int,
    val openedAt: Instant,
    val closed: Boolean = false,
    val attached: Boolean = false,
    val hasControl: Boolean? = null,
)

data class EqSpikeState(
    val listening: Boolean = false,
    val sessions: List<SpikeSession> = emptyList(),
    val events: List<SpikeEvent> = emptyList(),
)
