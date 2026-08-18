package eu.darken.bluemusic.eq.ui

import android.graphics.drawable.Drawable
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.eq.core.EqSessionState
import java.time.Instant

/**
 * The app behind an effect session.
 *
 * [label] and [icon] stay `null` until they are resolved, and when the package cannot be resolved at
 * all: the raw package name arrives on an unverified broadcast and is never shown to the user.
 *
 * Deliberately not marked stable: [Drawable] is mutable, and a promise Compose can't verify is worth
 * less than the recompositions it would save on a row this small.
 */
data class EqStatusApp(
    val packageName: String,
    val label: String? = null,
    val icon: Drawable? = null,
)

/**
 * What the equalizer is doing right now for the device the screen belongs to.
 *
 * `since` is when the session was announced to us, which is all we know: an app opens its session
 * when it gets ready to play, not necessarily when the first note comes out.
 */
sealed interface EqStatus {
    /** Another device (or none at all) owns the audio, so nothing we do here is audible. */
    data object InactiveForDevice : EqStatus

    /** This device is the target, but no app has announced a session to attach to yet. */
    data object Waiting : EqStatus

    /** At least one session is attached and ours to control. */
    data class Active(
        val app: EqStatusApp?,
        val multiple: Boolean,
        val since: Instant? = null,
    ) : EqStatus

    /** Sessions exist, but the framework kept control of their engine. */
    data class NoControl(
        val app: EqStatusApp?,
        val since: Instant? = null,
    ) : EqStatus
}

/**
 * Derives what to tell the user from the equalizer's target and the sessions it knows about.
 *
 * Only sessions we hold *and* have control over count as active: an attach the framework refused
 * control for changes nothing about the sound.
 */
internal fun deriveEqStatus(
    deviceAddress: DeviceAddr,
    eqEnabled: Boolean,
    hasCapabilities: Boolean,
    targetAddress: DeviceAddr?,
    sessionState: EqSessionState,
): EqStatus? {
    if (!eqEnabled || !hasCapabilities) return null
    if (targetAddress != deviceAddress) return EqStatus.InactiveForDevice

    val sessions = sessionState.openSessions
    if (sessions.isEmpty()) return EqStatus.Waiting

    val controlling = sessions.filter { it.attached && it.hasControl == true }
    if (controlling.isEmpty()) {
        val newest = sessions.maxByOrNull { it.openedAt }
        return EqStatus.NoControl(
            app = newest?.packageName?.let { EqStatusApp(it) },
            since = newest?.openedAt,
        )
    }

    val primary = controlling.filter { it.packageName != null }.maxByOrNull { it.openedAt }
    return EqStatus.Active(
        app = primary?.packageName?.let { EqStatusApp(it) },
        multiple = controlling.mapNotNull { it.packageName }.distinct().size > 1,
        // A session we can't name still tells us when it started.
        since = (primary ?: controlling.maxByOrNull { it.openedAt })?.openedAt,
    )
}
