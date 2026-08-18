package eu.darken.bluemusic.eq.ui

import android.content.pm.PackageManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.ca.CaString
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.SingleEventFlow
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.common.upgrade.isProForUi
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.observeDevice
import eu.darken.bluemusic.eq.core.EqCapabilities
import eu.darken.bluemusic.eq.core.EqConfigSaver
import eu.darken.bluemusic.eq.core.EqCoordinator
import eu.darken.bluemusic.eq.core.EqPresets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = DeviceEqViewModel.Factory::class)
class DeviceEqViewModel @AssistedInject constructor(
    @Assisted private val deviceAddress: DeviceAddr,
    private val deviceRepo: DeviceRepo,
    private val eqCapabilities: EqCapabilities,
    private val eqPresets: EqPresets,
    private val eqCoordinator: EqCoordinator,
    private val eqConfigSaver: EqConfigSaver,
    private val upgradeRepo: UpgradeRepo,
    private val packageManager: PackageManager,
    private val dispatcherProvider: DispatcherProvider,
    navCtrl: NavigationController,
) : ViewModel4(dispatcherProvider, logTag("Eq", "Device", "VM"), navCtrl) {

    data class State(
        val device: ManagedDevice,
        val capabilities: EqCapabilities.Caps?,
        val presets: List<PresetOption> = emptyList(),
        val isProVersion: Boolean = false,
        val status: EqStatus? = null,
    )

    sealed interface Event {
        data object RequiresPro : Event
    }

    val events = SingleEventFlow<Event>()

    /** A preset already interpolated to the engine we have, so the UI can match and apply it directly. */
    data class PresetOption(
        val id: EqPresets.Id,
        val label: CaString,
        val levels: List<Int>,
    )

    private var persistJob: Job? = null
    private var boostJob: Job? = null

    private val appCacheLock = Mutex()

    /**
     * Bounded, and the least recently used entry goes first: the package names come from an
     * unverified broadcast, so a misbehaving app must not be able to grow this without end.
     */
    private val appCache = object : LinkedHashMap<String, EqStatusApp>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EqStatusApp>): Boolean =
            size > APP_CACHE_SIZE
    }

    /**
     * Players announce a CLOSE/OPEN pair around every track change, and the sessions behind them come
     * and go with it. Debounced so the row states what is going on instead of flickering along.
     */
    private val statusFlow: Flow<EqStatus?> = combine(
        deviceRepo.observeDevice(deviceAddress).filterNotNull(),
        eqCapabilities.capabilities,
        eqCoordinator.targetAddress,
        eqCoordinator.sessionState,
    ) { device, capabilities, targetAddress, sessionState ->
        deriveEqStatus(
            deviceAddress = deviceAddress,
            eqEnabled = device.eqEnabled,
            hasCapabilities = capabilities != null,
            targetAddress = targetAddress,
            sessionState = sessionState,
        )
    }
        .distinctUntilChanged()
        .debounce(STATUS_DEBOUNCE)
        .mapLatest { status -> status.withResolvedApp() }

    val state = combine(
        deviceRepo.observeDevice(deviceAddress).filterNotNull(),
        eqCapabilities.capabilities.onStart { eqCapabilities.refreshIfNeeded() },
        upgradeRepo.upgradeInfo,
        statusFlow.onStart { emit(null) },
    ) { device, capabilities, upgradeInfo, status ->
        State(
            device = device,
            capabilities = capabilities,
            presets = capabilities?.let { caps ->
                eqPresets.presets.map { PresetOption(it.id, it.label, eqPresets.levelsFor(it.curve, caps)) }
            } ?: emptyList(),
            isProVersion = upgradeInfo.isPro,
            status = status,
        )
    }.asStateFlow()

    private suspend fun EqStatus?.withResolvedApp(): EqStatus? = when (this) {
        is EqStatus.Active -> copy(app = app?.resolve())
        is EqStatus.NoControl -> copy(app = app?.resolve())
        else -> this
    }

    /**
     * Turns a package name into something we can show. A package we can't resolve stays unresolved:
     * the name itself comes from an unverified broadcast and is not ours to display.
     */
    private suspend fun EqStatusApp.resolve(): EqStatusApp = appCacheLock.withLock {
        appCache.getOrPut(packageName) {
            withContext(dispatcherProvider.IO) {
                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    EqStatusApp(
                        packageName = packageName,
                        label = appInfo.loadLabel(packageManager).toString(),
                        icon = appInfo.loadIcon(packageManager),
                    )
                } catch (e: Exception) {
                    log(tag, WARN) { "Failed to resolve $packageName: ${e.asLog()}" }
                    EqStatusApp(packageName)
                }
            }
        }
    }

    /**
     * Entitlement is checked here instead of against the state field: the state can still carry a
     * cold-start "not pro" from before billing settled, and that must not cost a paying user the
     * switch.
     */
    fun onToggleEq() = launch {
        if (!upgradeRepo.isProForUi()) {
            log(tag) { "onToggleEq(): Not pro" }
            events.emit(Event.RequiresPro)
            return@launch
        }
        deviceRepo.updateDevice(deviceAddress) { oldConfig ->
            oldConfig.copy(eqEnabled = !oldConfig.eqEnabled)
        }
    }

    /** Live values while a slider is being dragged: applied to the running effects, not persisted. */
    fun onLevelsChanged(levels: List<Int>) {
        eqCoordinator.previewLevels(deviceAddress, levels)
    }

    fun onLevelsCommitted(levels: List<Int>) {
        log(tag) { "onLevelsCommitted($levels)" }
        persistJob?.cancel()
        // The write itself runs on the app scope, so leaving the screen right after a slider release
        // cannot lose it. This job only waits for it to sequence the preview clear.
        val write = eqConfigSaver.save(deviceAddress) { it.copy(eqBandLevels = levels) }
        persistJob = vmScope.launch {
            // In a finally throughout: a cancelled wait must not leave a preview curve applied to the
            // running effects, it would outlive this screen and never be cleared by anyone else.
            try {
                write.await()
            } finally {
                eqCoordinator.previewLevels(deviceAddress, null)
            }
        }
    }

    /** Live boost while the slider is being dragged: applied to the running effects, not persisted. */
    fun onBoostChanged(gain: Int) {
        eqCoordinator.previewBoost(deviceAddress, gain)
    }

    fun onBoostCommitted(gain: Int) {
        log(tag) { "onBoostCommitted($gain)" }
        boostJob?.cancel()
        // No boost is the "never configured" state, storing null keeps the enhancer out of it.
        val write = eqConfigSaver.save(deviceAddress) { it.copy(eqBoostGain = gain.takeIf { value -> value > 0 }) }
        boostJob = vmScope.launch {
            try {
                write.await()
            } finally {
                eqCoordinator.previewBoost(deviceAddress, null)
            }
        }
    }

    fun applyPreset(id: EqPresets.Id) {
        log(tag) { "applyPreset($id)" }
        // The preset chips are only rendered once the capabilities are known, so a missing option means
        // there is nothing to interpolate the curve against yet.
        val preset = state.value?.presets?.firstOrNull { it.id == id }
        if (preset == null) {
            log(tag, WARN) { "applyPreset($id): No such preset, capabilities aren't loaded" }
            return
        }
        // Flat is the "never configured" state, not a curve of zeroes: storing null keeps the device
        // out of the equalizer's way entirely.
        val levels = preset.levels.takeIf { id != EqPresets.Id.FLAT }
        // Only the wait for a slider release is dropped, its write is already queued: the preset is
        // enqueued behind it and therefore still the value that ends up stored.
        persistJob?.cancel()
        // Like a slider commit, the write goes to the app scope before any coroutine of ours exists,
        // so tapping a chip and leaving the screen in the same moment cannot lose it.
        val write = eqConfigSaver.save(deviceAddress) { it.copy(eqBandLevels = levels) }
        persistJob = vmScope.launch {
            try {
                write.await()
            } finally {
                eqCoordinator.previewLevels(deviceAddress, null)
            }
        }
    }

    override fun onCleared() {
        // Leaving the screen mid-drag cancels the scope, so nothing else would clear the preview.
        eqCoordinator.previewLevels(deviceAddress, null)
        eqCoordinator.previewBoost(deviceAddress, null)
        super.onCleared()
    }

    @AssistedFactory
    interface Factory {
        fun create(deviceAddress: DeviceAddr): DeviceEqViewModel
    }

    companion object {
        /** How long the session picture has to hold still before the status row follows it. */
        private val STATUS_DEBOUNCE = 400.milliseconds

        /** Upper bound on remembered app resolutions, successful or not. */
        private const val APP_CACHE_SIZE = 32
    }
}
