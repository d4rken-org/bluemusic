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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
 * All desired-state changes go through one serialized actor with a sequence check, so a recompute
 * that was computed before a newer release can never re-attach after it.
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
    private val sequence = AtomicLong(0L)
    private var appliedSequence = 0L
    private var appliedTarget: Target? = null

    private val sessionLock = Mutex()
    private var sessionJob: Job? = null

    val sessionState = tracker.state

    /** Starts reacting to ownership and config changes. Called when a monitor session starts. */
    suspend fun startSession() = sessionLock.withLock {
        if (sessionJob?.isActive == true) {
            log(TAG, VERBOSE) { "startSession(): Already running" }
            return@withLock
        }
        log(TAG, INFO) { "startSession()" }
        sessionJob = appScope.launch(dispatcherProvider.Default) {
            try {
                runSession()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "Session failed: ${e.asLog()}" }
            } finally {
                withContext(NonCancellable) { release("Session ended") }
            }
        }
    }

    /** Releases every effect and stops listening. Safe to call more than once. */
    suspend fun stopSession() = withContext(NonCancellable) {
        val job = sessionLock.withLock { sessionJob.also { sessionJob = null } }
        log(TAG, INFO) { "stopSession()" }
        job?.cancelAndJoin()
        release("Session stopped")
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
        if (enabled) {
            tracker.startListening()
        } else {
            appliedTarget = null
            controller.detachAll()
            tracker.stopListening()
        }
    }

    suspend fun clearDiagnostics() = tracker.clear()

    private suspend fun runSession() {
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

        val openSessionIds = tracker.state
            .map { state -> state.openSessions.map { it.sessionId } }
            .distinctUntilChanged()

        targets
            .combine(openSessionIds) { target, sessionIds -> target to sessionIds }
            .collect { (target, sessionIds) -> submit(target, sessionIds) }
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

    private suspend fun submit(target: Target?, sessionIds: List<Int>) = act("apply($target, $sessionIds)") {
        if (target == null) {
            if (appliedTarget == null && controller.attachedSessionIds().isEmpty() && !tracker.state.value.listening) {
                return@act
            }
            log(TAG, INFO) { "No eligible owner, releasing everything" }
            appliedTarget = null
            controller.detachAll()
            tracker.stopListening()
            return@act
        }

        if (!tracker.state.value.listening) tracker.startListening()

        val attached = controller.attachedSessionIds()
        val wanted = sessionIds.toSet()

        (attached - wanted).forEach { controller.detach(it) }

        val levelsChanged = appliedTarget?.levels != target.levels
        appliedTarget = target

        if (levelsChanged && (attached intersect wanted).isNotEmpty()) controller.updateLevels(target.levels)

        (wanted - attached).forEach { controller.attach(it, target.levels) }
    }

    private suspend fun release(reason: String) = act("release($reason)") {
        appliedTarget = null
        previewFlow.value = null
        controller.detachAll()
        tracker.stopListening()
    }

    /**
     * Runs [block] on the single actor. Work computed before a newer change was already applied is
     * dropped instead of being applied out of order.
     */
    private suspend fun act(label: String, block: suspend () -> Unit) {
        val seq = sequence.incrementAndGet()
        actorLock.withLock {
            if (seq <= appliedSequence) {
                log(TAG, WARN) { "Dropping stale $label (seq=$seq, applied=$appliedSequence)" }
                return
            }
            appliedSequence = seq
            log(TAG, VERBOSE) { "Applying $label (seq=$seq)" }
            block()
        }
    }

    companion object {
        private val TAG = logTag("Eq", "Coordinator")
        private val ENTITLEMENT_TIMEOUT = 3.seconds
    }
}
