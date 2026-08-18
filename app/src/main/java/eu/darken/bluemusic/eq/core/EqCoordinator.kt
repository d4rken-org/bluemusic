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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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
 * Decides when the equalizer runs, with which levels and how much volume boost.
 *
 * While a device that owns the audio streams has the equalizer enabled, we listen for effect
 * session broadcasts and attach our curve to every session cooperating apps announce. Losing
 * ownership, disabling the feature, or the monitor session ending releases everything again.
 *
 * A broadcast CLOSE only starts a [CLOSE_GRACE] timer instead of releasing right away: players
 * announce a close/open pair around every track change while keeping the same session, and letting
 * go in between costs the listener a moment of un-equalized audio. Every deliberate teardown still
 * releases immediately.
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
        val boostGain: Int,
    )

    /** Transient edits of the config screen, `null` fields fall back to what is stored. */
    private data class Preview(
        val address: DeviceAddr,
        val levels: List<Int>? = null,
        val boostGain: Int? = null,
    ) {
        val isEmpty: Boolean
            get() = levels == null && boostGain == null
    }

    /** A session that broadcast CLOSE and is kept attached until its grace timer fires. */
    private data class PendingRelease(
        val job: Job,
        /** What the session was running with when it closed, to spot edits made while it waited. */
        val target: Target?,
    )

    private val previewFlow = MutableStateFlow<Preview?>(null)

    private val actorLock = Mutex()
    private var appliedTarget: Target? = null

    /** Session ids the transition stream told us about, only touched from inside the actor. */
    private val openSessionIds = mutableSetOf<Int>()

    /** Closed sessions we still hold, keyed by session id. Only touched from inside the actor. */
    private val pendingReleases = mutableMapOf<Int, PendingRelease>()

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
     *
     * The whole teardown runs under [sessionLock], so [startSession] cannot install a new token
     * while a previous stop is still cancelling and releasing. The cancelled job's own release runs
     * inside its `finally`, which [cancelAndJoin] waits for, so it can never outlive the join.
     */
    suspend fun stopSession(token: Long) = withContext(NonCancellable) {
        sessionLock.withLock {
            val active = activeToken.get()
            if (active != token) {
                log(TAG, INFO) { "stopSession($token): Not the active session ($active), ignoring" }
                return@withLock
            }
            log(TAG, INFO) { "stopSession($token)" }
            activeToken.set(NO_SESSION)
            sessionJob?.cancelAndJoin()
            release("Session $token stopped")
            sessionJob = null
        }
    }

    /**
     * Transient levels for live preview while a band slider is being dragged. They bypass persistence
     * and only apply while [address] is the device the equalizer is currently running for.
     */
    fun previewLevels(address: DeviceAddr, levels: List<Int>?) {
        log(TAG, VERBOSE) { "previewLevels($address, $levels)" }
        updatePreview(address) { it.copy(levels = levels) }
    }

    /** Transient boost gain for live preview while the boost slider is being dragged. */
    fun previewBoost(address: DeviceAddr, boostGain: Int?) {
        log(TAG, VERBOSE) { "previewBoost($address, $boostGain)" }
        updatePreview(address) { it.copy(boostGain = boostGain) }
    }

    /**
     * Bands and boost are dragged one at a time but preview together, so an edit for another device
     * replaces the preview instead of merging into it.
     */
    private fun updatePreview(address: DeviceAddr, update: (Preview) -> Preview) {
        previewFlow.update { current ->
            val base = current?.takeIf { it.address == address } ?: Preview(address)
            update(base).takeUnless { it.isEmpty }
        }
    }

    /** Manual listening override for the diagnostics screen. */
    suspend fun setListening(enabled: Boolean) = act("setListening($enabled)") {
        cancelPendingReleases("setListening($enabled)")
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
        // GPlay cold start reports non-Pro until billing settles, so reconcile once (fail-open). The
        // answer only ever adds entitlement: a session that started while billing was down must not
        // stay idle for its whole lifetime, and a purchase made while it runs has to take effect.
        val reconciled = upgradeRepo.isProSettled(ENTITLEMENT_TIMEOUT)
        log(TAG, INFO) { "Session $token entitlement reconciled: $reconciled" }

        val operational = combine(
            eligibility.hasEngine,
            upgradeRepo.upgradeInfo.map { reconciled || it.isPro }.distinctUntilChanged(),
        ) { hasEngine, entitled -> hasEngine && entitled }.distinctUntilChanged()

        val targets = combine(
            deviceRepo.devices,
            ownerRegistry.ownerSnapshots,
            operational,
            devicesSettings.enabledState.map { it.isEnabled }.distinctUntilChanged(),
            previewFlow,
        ) { devices, owner, isOperational, appEnabled, preview ->
            resolveTarget(devices, owner, isOperational, appEnabled, preview)
        }.distinctUntilChanged()

        coroutineScope {
            val sessionScope = this
            launch { consumeTransitions(token, sessionScope) }
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
    private suspend fun consumeTransitions(token: Long, sessionScope: CoroutineScope) {
        while (currentCoroutineContext().isActive) {
            val stream = tracker.transitions()
            try {
                for (transition in stream) applyTransition(token, transition, sessionScope)
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

        val active = preview?.takeIf { it.address == chosen.address }
        val levels = active?.levels ?: chosen.eqBandLevels ?: emptyList()
        val boostGain = active?.boostGain ?: chosen.eqBoostGain ?: 0

        return Target(address = chosen.address, levels = levels, boostGain = boostGain)
    }

    private suspend fun applyTarget(token: Long, target: Target?) = act("target($target)", token) {
        if (target == null) {
            if (appliedTarget == null && controller.attachedSessionIds().isEmpty() && !tracker.state.value.listening) {
                return@act
            }
            log(TAG, INFO) { "No eligible owner, releasing everything" }
            appliedTarget = null
            cancelPendingReleases("No eligible owner")
            openSessionIds.clear()
            controller.detachAll()
            tracker.stopListening()
            return@act
        }

        if (!tracker.state.value.listening) {
            // A fresh generation knows no sessions yet, they arrive as OPEN transitions.
            cancelPendingReleases("New listening generation")
            openSessionIds.clear()
            tracker.startListening()
        }

        val previous = appliedTarget
        appliedTarget = target

        // Sessions waiting out their grace stay in both sets, so they reconcile like any other one:
        // an edit reaches them, and neither branch below touches them.
        val attached = controller.attachedSessionIds()
        val live = (attached intersect openSessionIds).isNotEmpty()
        if (live && previous?.levels != target.levels) controller.updateLevels(target.levels)
        if (live && previous?.boostGain != target.boostGain) controller.updateBoost(target.boostGain)

        (attached - openSessionIds).forEach { controller.detach(it) }
        (openSessionIds - attached).forEach { controller.attach(it, target.levels, target.boostGain) }
    }

    private suspend fun applyTransition(token: Long, transition: EqTransition, sessionScope: CoroutineScope) =
        act("transition($transition)", token) {
            val state = tracker.state.value
            if (!state.listening || transition.generation != state.generation) {
                log(TAG, VERBOSE) { "Dropping $transition, now at gen=${state.generation} listening=${state.listening}" }
                return@act
            }

            val sessionId = transition.sessionId
            when (transition.type) {
                EqTransition.Type.OPEN -> {
                    if (resurrect(sessionId)) return@act
                    openSessionIds += sessionId
                    val target = appliedTarget ?: return@act
                    // A fresh id is a fresh engine, not the one we held for it before.
                    controller.attach(sessionId, target.levels, target.boostGain)
                }

                EqTransition.Type.CLOSE -> {
                    if (sessionId !in openSessionIds || sessionId !in controller.attachedSessionIds()) {
                        // Nothing of ours is playing through it, so there is nothing to keep alive.
                        openSessionIds -= sessionId
                        controller.detach(sessionId)
                        return@act
                    }
                    if (pendingReleases.containsKey(sessionId)) {
                        log(TAG, VERBOSE) { "Session $sessionId is already pending release" }
                        return@act
                    }
                    log(TAG, INFO) { "Session $sessionId closed, holding its effect for $CLOSE_GRACE" }
                    val job = sessionScope.launch {
                        delay(CLOSE_GRACE)
                        expireGrace(token, sessionId)
                    }
                    pendingReleases[sessionId] = PendingRelease(job = job, target = appliedTarget)
                }
            }
        }

    /**
     * Takes [sessionId] back out of its grace period when it reopens, returning whether it was
     * pending at all.
     *
     * The engine behind a reopened id is the one we already configured, so it is kept as is instead
     * of being torn down and set up again. Only an edit made while it waited still has to land.
     */
    private suspend fun resurrect(sessionId: Int): Boolean {
        val pending = pendingReleases.remove(sessionId) ?: return false
        pending.job.cancel()

        if (sessionId !in controller.attachedSessionIds()) {
            log(TAG, WARN) { "Session $sessionId reopened but its effect is gone, attaching again" }
            return false
        }

        log(TAG, INFO) { "Session $sessionId reopened within the grace period, keeping its effect" }
        val target = appliedTarget ?: return true
        if (pending.target?.levels != target.levels) controller.updateLevels(target.levels)
        if (pending.target?.boostGain != target.boostGain) controller.updateBoost(target.boostGain)
        return true
    }

    private suspend fun expireGrace(token: Long, sessionId: Int) = act("graceExpired($sessionId)", token) {
        if (pendingReleases.remove(sessionId) == null) return@act
        log(TAG, INFO) { "Grace period for session $sessionId expired, releasing it" }
        openSessionIds -= sessionId
        controller.detach(sessionId)

        // The released slot may be what a session opened during the grace was turned away for.
        val target = appliedTarget ?: return@act
        (openSessionIds - controller.attachedSessionIds()).forEach {
            log(TAG, INFO) { "Session $it has no effect yet, attaching it into the freed slot" }
            controller.attach(it, target.levels, target.boostGain)
        }
    }

    /**
     * Drops every grace timer without releasing anything: the caller is a teardown that lets go of
     * the effects itself.
     */
    private fun cancelPendingReleases(reason: String) {
        if (pendingReleases.isEmpty()) return
        log(TAG, INFO) { "Releasing ${pendingReleases.size} session(s) ahead of their grace period: $reason" }
        pendingReleases.values.forEach { it.job.cancel() }
        pendingReleases.clear()
    }

    /** Drops everything we think we know and starts over on a fresh listening generation. */
    private suspend fun restartListening(token: Long) = act("restartListening()", token) {
        cancelPendingReleases("Restarting listening")
        openSessionIds.clear()
        controller.detachAll()
        tracker.stopListening()
        if (appliedTarget != null) tracker.startListening()
    }

    private suspend fun release(reason: String) = act("release($reason)") {
        appliedTarget = null
        cancelPendingReleases(reason)
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

        /** How long a session that broadcast CLOSE keeps its effect in case it comes right back. */
        private val CLOSE_GRACE = 3.seconds

        /** [activeToken] value while no session is running. Real tokens start at 1. */
        private const val NO_SESSION = 0L
    }
}
