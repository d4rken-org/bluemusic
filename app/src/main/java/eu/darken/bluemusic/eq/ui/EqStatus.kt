package eu.darken.bluemusic.eq.ui

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.eq.core.EqSessionState

/**
 * The app behind an effect session.
 *
 * [label] and [icon] stay `null` until they are resolved, and when the package cannot be resolved at
 * all: the raw package name arrives on an unverified broadcast and is never shown to the user.
 */
@Immutable
data class EqStatusApp(
    val packageName: String,
    val label: String? = null,
    val icon: Drawable? = null,
)

/** What the equalizer is doing right now for the device the screen belongs to. */
sealed interface EqStatus {
    /** Another device (or none at all) owns the audio, so nothing we do here is audible. */
    data object InactiveForDevice : EqStatus

    /** This device is the target, but no app has announced a session to attach to yet. */
    data object Waiting : EqStatus

    /** At least one session is attached and ours to control. */
    data class Active(val app: EqStatusApp?, val multiple: Boolean) : EqStatus

    /** Sessions exist, but the framework kept control of their engine. */
    data class NoControl(val app: EqStatusApp?) : EqStatus
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
        val newest = sessions.maxByOrNull { it.openedAt }?.packageName
        return EqStatus.NoControl(app = newest?.let { EqStatusApp(it) })
    }

    val primary = controlling
        .filter { it.packageName != null }
        .maxByOrNull { it.openedAt }
        ?.packageName
    return EqStatus.Active(
        app = primary?.let { EqStatusApp(it) },
        multiple = controlling.mapNotNull { it.packageName }.distinct().size > 1,
    )
}
