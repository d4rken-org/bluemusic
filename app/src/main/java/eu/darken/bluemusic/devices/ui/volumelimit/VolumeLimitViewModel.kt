package eu.darken.bluemusic.devices.ui.volumelimit

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.SingleEventFlow
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.core.DeviceConfigSaver
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.ToggleResult
import eu.darken.bluemusic.devices.core.observeDevice
import eu.darken.bluemusic.devices.core.toggleVolumeLimit
import eu.darken.bluemusic.devices.core.withVolumeLimit
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull

@HiltViewModel(assistedFactory = VolumeLimitViewModel.Factory::class)
class VolumeLimitViewModel @AssistedInject constructor(
    @Assisted private val deviceAddress: DeviceAddr,
    private val deviceRepo: DeviceRepo,
    private val configSaver: DeviceConfigSaver,
    private val upgradeRepo: UpgradeRepo,
    private val volumeTool: VolumeTool,
    dispatcherProvider: DispatcherProvider,
    navCtrl: NavigationController,
) : ViewModel4(dispatcherProvider, logTag("Devices", "VolumeLimit", "VM"), navCtrl) {

    data class State(
        val device: ManagedDevice,
        val isProVersion: Boolean = false,
        /** Steps a managed stream offers, absent when the level count couldn't be determined. */
        val volumeStepCounts: Map<AudioStream.Type, Int> = emptyMap(),
    )

    sealed interface Event {
        data object RequiresPro : Event
    }

    val events = SingleEventFlow<Event>()

    val state = combine(
        deviceRepo.observeDevice(deviceAddress).filterNotNull(),
        upgradeRepo.upgradeInfo,
    ) { device, upgradeInfo ->
        State(
            device = device,
            isProVersion = upgradeInfo.isPro,
            volumeStepCounts = volumeStepCounts(device),
        )
    }.asStateFlow()

    /**
     * Steps between a stream's lowest and highest hardware level, i.e. `max - min`. Lets the limit
     * slider land on levels the device actually has instead of on percentages that resolve to one.
     */
    private fun volumeStepCounts(device: ManagedDevice): Map<AudioStream.Type, Int> = AudioStream.Type.entries
        .filter { device.getVolume(it) != null }
        .mapNotNull { type ->
            val streamId = device.getStreamId(type)
            val span = try {
                volumeTool.getMaxVolume(streamId) - volumeTool.getMinVolume(streamId)
            } catch (e: Exception) {
                log(tag, WARN) { "Can't determine level count for $type: ${e.asLog()}" }
                null
            }
            span?.takeIf { it > 0 }?.let { type to it }
        }
        .toMap()

    fun onToggleLimit() = launch {
        val result = deviceRepo.toggleVolumeLimit(deviceAddress, upgradeRepo)
        if (result == ToggleResult.NOT_PRO) events.emit(Event.RequiresPro)
    }

    fun onLimitChanged(type: AudioStream.Type, min: Float?, max: Float?) {
        log(tag) { "onLimitChanged($type, $min, $max)" }
        // Handed over on the caller's thread and written on the app scope: releasing a thumb and
        // leaving the screen in the same moment would otherwise cancel the value just committed.
        configSaver.save(deviceAddress) { it.withVolumeLimit(type, min, max) }
    }

    @AssistedFactory
    interface Factory {
        fun create(deviceAddress: DeviceAddr): VolumeLimitViewModel
    }
}
