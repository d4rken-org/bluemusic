package eu.darken.bluemusic.bluetooth.ui.discover

import android.app.Activity
import eu.darken.bluemusic.bluetooth.core.speaker.FakeSpeakerDevice
import eu.darken.bluemusic.bluetooth.core.LiveBluetoothSourceFlow
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.architecture.BaseViewModel
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.devices.core.DeviceRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.*
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import javax.inject.Inject

data class DiscoverState(
    val availableDevices: List<SourceDevice> = emptyList(),
    val isLoading: Boolean = false,
    val isProVersion: Boolean = false,
    val managedDeviceCount: Int = 0,
    val error: String? = null,
    val showUpgradeDialog: Boolean = false,
    val shouldClose: Boolean = false
)

sealed interface DiscoverEvent {
    data class OnDeviceSelected(val device: SourceDevice) : DiscoverEvent
    data class OnPurchaseUpgrade(val activity: Activity) : DiscoverEvent
    data object OnDismissDialog : DiscoverEvent
    data object OnRefresh : DiscoverEvent
}

class DiscoverViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val bluetoothSource: LiveBluetoothSourceFlow,
    private val upgradeRepo: UpgradeRepo,
    private val dispatcherProvider: DispatcherProvider
) : BaseViewModel<DiscoverState, DiscoverEvent>(DiscoverState()) {

    companion object {
        private val TAG = logTag("DiscoverViewModel")
    }
    
    init {
        observeProVersion()
        loadAvailableDevices()
    }
    
    private fun observeProVersion() {
//        launch {
//            iapRepo.recheck()
//            iapRepo.isProVersion
//                .catch { e ->
//                    log(TAG, ERROR) { "Failed to observe pro version: ${e.asLog()}" }
//                }
//                .collect { isProVersion ->
//                    updateState { copy(isProVersion = isProVersion) }
//                }
//        }
    }
    
    private fun loadAvailableDevices() {
        launch {
            updateState { copy(isLoading = true, error = null) }
            
            try {
                combine(
                    deviceRepository.getAllDevices(),
                    bluetoothSource.connectedDevices
                ) { managedDevices, pairedDevices ->
                    val managedAddresses = managedDevices.map { it.address }.toSet()
                    val availableDevices = pairedDevices.values
                        .filter { device -> device.address !in managedAddresses }
                        .sortedBy { device ->
                            when (device.address) {
                                FakeSpeakerDevice.address -> 0
                                else -> 1
                            }
                        }
                    
                    updateState {
                        copy(
                            availableDevices = availableDevices,
                            managedDeviceCount = managedDevices.size,
                            isLoading = false
                        )
                    }
                }.collect()
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to load available devices: ${e.asLog()}" }
                updateState {
                    copy(
                        error = e.message,
                        isLoading = false
                    )
                }
            }
        }
    }
    
    override fun onEvent(event: DiscoverEvent) {
        when (event) {
            is DiscoverEvent.OnDeviceSelected -> addDevice(event.device)
            is DiscoverEvent.OnPurchaseUpgrade -> purchaseUpgrade(event.activity)
            is DiscoverEvent.OnDismissDialog -> dismissDialog()
            is DiscoverEvent.OnRefresh -> loadAvailableDevices()
        }
    }
    
    private fun addDevice(device: SourceDevice) {
        if (!currentState.isProVersion && currentState.managedDeviceCount > 2) {
            updateState { copy(showUpgradeDialog = true) }
            return
        }
        
        launch {
            updateState { copy(isLoading = true) }
            
            try {
                log(TAG, INFO) { "Adding new device: $device" }
                deviceRepository.createDevice(
                    address = device.address
                )
                updateState { copy(shouldClose = true) }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to add device: ${e.asLog()}" }
                updateState {
                    copy(
                        error = e.message,
                        isLoading = false
                    )
                }
            }
        }
    }
    
    private fun purchaseUpgrade(activity: Activity) {
        launch {
            // TODO: Implement purchase flow
            // iapRepo.startIAPFlow(AvailableSkus.PRO_VERSION, activity)
            dismissDialog()
        }
    }
    
    private fun dismissDialog() {
        updateState { copy(showUpgradeDialog = false) }
    }
}
