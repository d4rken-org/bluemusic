package eu.darken.bluemusic.eq.core

import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.common.upgrade.isProSettled
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import eu.darken.bluemusic.monitor.core.ownership.OwnerSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Decides when the equalizer runs and with which levels.
 *
 * While a device that owns the audio streams has the equalizer enabled, we listen for effect
 * session broadcasts and attach our curve to every session cooperating apps announce. Losing
 * ownership, disabling the feature, or the monitor session ending releases everything again.
 *
 * All desired-state changes go through one serialized actor tagged with the session token they were
 * computed for, so work from a session that has already ended can never re-attach after its release.
 */
@Singleton
class EqCoordinator @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val deviceRepo: DeviceRepo,
    private val ownerRegistry: AudioStreamOwnerRegistry,
    private val eligibility: EqEligibility,
    private val tracker: EqSessionTracker,
    private val controller: EqEffectController,
    private val devicesSettings: DevicesSettings,
    private val upgradeRepo: UpgradeRepo,
) {

    data class Target(
        val address: DeviceAddr,
        val levels: List<Int>,
    )

    private data class Preview(
        val address: DeviceAddr,
        val levels: List<Int>,
    )

    private val previewFlow = MutableStateFlow<Preview?>(null)

    private val actorLock = Mutex()
    private var appliedTarget: Target? = null

    /** Session ids the transition stream told us about, only touched from inside the actor. */
    private val openSessionIds = mutableSetOf<Int>()

    private val sessionLock = Mutex()
    private var sessionJob: Job? = null
    private var sessionCounter = 0L

    /** The token of the session that is allowed to act right now, [NO_SESSION] while none runs. */
    private val activeToken = AtomicLong(NO_SESSION)

    val sessionState = tracker.state

    /**
     * Starts reacting to ownership and config changes. Called when a monitor session starts.
     *
     * A session that is still running is cancelled and joined first, so the returned token always
     * belongs to the only session that can act from here on.
     */
    suspend fun startSession(): Long = withContext(NonCancellable) {
        sessionLock.withLock {
            sessionJob?.let { previous ->
                log(TAG, INFO) { "startSession(): Replacing the running session" }
                activeToken.set(NO_SESSION)
                previous.cancelAndJoin()
            }

            val token = ++sessionCounter
            activeToken.set(token)
            log(TAG, INFO) { "startSession(): token=$token" }

            sessionJob = appScope.launch(dispatcherProvider.Default) {
                try {
                    runSession(token)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, WARN) { "Session failed: ${e.asLog()}" }
                } finally {
                    withContext(NonCancellable) { release("Session $token ended") }
                }
            }
            token
        }
    }

    /**
     * Releases every effect and stops listening, but only for the session [token] identifies. A
     * caller whose session was already replaced no-ops instead of tearing down the newer one.
     */
    suspend fun stopSession(token: Long) = withContext(NonCancellable) {
        val job = sessionLock.withLock {
            val active = activeToken.get()
            if (active != token) {
                log(TAG, INFO) { "stopSession($token): Not the active session ($active), ignoring" }
                return@withContext
            }
            activeToken.set(NO_SESSION)
            sessionJob.also { sessionJob = null }
        }
        log(TAG, INFO) { "stopSession($token)" }
        job?.cancelAndJoin()
        release("Session $token stopped")
    }

    /**
     * Transient levels for live preview while a slider is being dragged. They bypass persistence and
     * only apply while [address] is the device the equalizer is currently running for.
     */
    fun previewLevels(address: DeviceAddr, levels: List<Int>?) {
        log(TAG, VERBOSE) { "previewLevels($address, $levels)" }
        previewFlow.value = levels?.let { Preview(address, it) }
    }

    /** Manual listening override for the diagnostics screen. */
    suspend fun setListening(enabled: Boolean) = act("setListening($enabled)") {
        openSessionIds.clear()
        if (enabled) {
            tracker.startListening()
        } else {
            appliedTarget = null
            controller.detachAll()
            tracker.stopListening()
        }
    }

    suspend fun clearDiagnostics() = tracker.clear()

    private suspend fun runSession(token: Long) {
        // GPlay cold start reports non-Pro until billing settles, so reconcile once (fail-open)
        // before trusting the cached entitlement flow for the rest of the session.
        if (!upgradeRepo.isProSettled(ENTITLEMENT_TIMEOUT)) {
            log(TAG, INFO) { "Not entitled to the equalizer, session stays idle" }
            return
        }

        val targets = combine(
            deviceRepo.devices,
            ownerRegistry.ownerSnapshots,
            eligibility.operational,
            devicesSettings.enabledState.map { it.isEnabled }.distinctUntilChanged(),
            previewFlow,
        ) { devices, owner, operational, appEnabled, preview ->
            resolveTarget(devices, owner, operational, appEnabled, preview)
        }.distinctUntilChanged()

        coroutineScope {
            launch { consumeTransitions(token) }
            targets.collect { target -> applyTarget(token, target) }
        }
    }

    /**
     * Applies session edges in the order they arrived.
     *
     * The stream ending means an edge was lost, and our idea of what is open is wrong from there on:
     * everything is released and a fresh listening generation is started instead of carrying the
     * wrong state forward.
     */
    private suspend fun consumeTransitions(token: Long) {
        while (currentCoroutineContext().isActive) {
            val stream = tracker.transitions()
            try {
                for (transition in stream) applyTransition(token, transition)
                log(TAG, WARN) { "Transition stream closed, restarting" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "Transition stream failed, restarting: ${e.asLog()}" }
            }
            restartListening(token)
        }
    }

    private fun resolveTarget(
        devices: List<ManagedDevice>,
        owner: OwnerSnapshot,
        operational: Boolean,
        appEnabled: Boolean,
        preview: Preview?,
    ): Target? {
        if (!operational || !appEnabled) return null

        val ownerAddresses = owner.ownerAddresses
        if (ownerAddresses.isEmpty()) return null

        val candidates = devices.filter { it.address in ownerAddresses && it.isActive && it.eqEnabled }
        // Grouped owners (e.g. two earbuds) can hold several configs, the most recently connected wins.
        val chosen = candidates.maxByOrNull { it.config.lastConnected } ?: return null
        if (candidates.size > 1) {
            log(TAG) { "Owner group has ${candidates.size} equalizer configs, using ${chosen.address}" }
        }

        val levels = preview?.takeIf { it.address == chosen.address }?.levels
            ?: chosen.eqBandLevels
            ?: emptyList()

        return Target(address = chosen.address, levels = levels)
    }

    private suspend fun applyTarget(token: Long, target: Target?) = act("target($target)", token) {
        if (target == null) {
            if (appliedTarget == null && controller.attachedSessionIds().isEmpty() && !tracker.state.value.listening) {
                return@act
            }
            log(TAG, INFO) { "No eligible owner, releasing everything" }
            appliedTarget = null
            openSessionIds.clear()
            controller.detachAll()
            tracker.stopListening()
            return@act
        }

        if (!tracker.state.value.listening) {
            // A fresh generation knows no sessions yet, they arrive as OPEN transitions.
            openSessionIds.clear()
            tracker.startListening()
        }

        val levelsChanged = appliedTarget?.levels != target.levels
        appliedTarget = target

        val attached = controller.attachedSessionIds()
        if (levelsChanged && (attached intersect openSessionIds).isNotEmpty()) controller.updateLevels(target.levels)

        (attached - openSessionIds).forEach { controller.detach(it) }
        (openSessionIds - attached).forEach { controller.attach(it, target.levels) }
    }

    private suspend fun applyTransition(token: Long, transition: EqTransition) =
        act("transition($transition)", token) {
            val state = tracker.state.value
            if (!state.listening || transition.generation != state.generation) {
                log(TAG, VERBOSE) { "Dropping $transition, now at gen=${state.generation} listening=${state.listening}" }
                return@act
            }

            when (transition.type) {
                EqTransition.Type.OPEN -> {
                    openSessionIds += transition.sessionId
                    val target = appliedTarget ?: return@act
                    // Always a fresh attach: a reopened id is a new engine, not the one we held.
                    controller.attach(transition.sessionId, target.levels)
                }

                EqTransition.Type.CLOSE -> {
                    openSessionIds -= transition.sessionId
                    controller.detach(transition.sessionId)
                }
            }
        }

    /** Drops everything we think we know and starts over on a fresh listening generation. */
    private suspend fun restartListening(token: Long) = act("restartListening()", token) {
        openSessionIds.clear()
        controller.detachAll()
        tracker.stopListening()
        if (appliedTarget != null) tracker.startListening()
    }

    private suspend fun release(reason: String) = act("release($reason)") {
        appliedTarget = null
        openSessionIds.clear()
        previewFlow.value = null
        controller.detachAll()
        tracker.stopListening()
    }

    /**
     * Runs [block] on the single actor. Work tagged with a [token] that is no longer the active
     * session's is dropped, so an attach that was computed before a release cannot outlive it.
     * Untagged work (releases, the diagnostics override) always applies.
     */
    private suspend fun act(label: String, token: Long? = null, block: suspend () -> Unit) {
        actorLock.withLock {
            val active = activeToken.get()
            if (token != null && token != active) {
                log(TAG, WARN) { "Dropping stale $label (token=$token, active=$active)" }
                return
            }
            log(TAG, VERBOSE) { "Applying $label" }
            block()
        }
    }

    companion object {
        private val TAG = logTag("Eq", "Coordinator")
        private val ENTITLEMENT_TIMEOUT = 3.seconds

        /** [activeToken] value while no session is running. Real tokens start at 1. */
        private const val NO_SESSION = 0L
    }
}
