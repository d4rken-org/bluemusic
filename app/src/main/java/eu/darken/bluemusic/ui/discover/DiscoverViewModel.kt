package eu.darken.bluemusic.ui.discover

import android.app.Activity
import eu.darken.bluemusic.bluetooth.core.BluetoothSource
import eu.darken.bluemusic.bluetooth.core.FakeSpeakerDevice
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.architecture.BaseViewModel
import eu.darken.bluemusic.common.coroutines.DispatcherProvider
import eu.darken.bluemusic.data.device.DeviceRepository
import eu.darken.bluemusic.util.iap.IAPRepo
import kotlinx.coroutines.flow.*
import timber.log.Timber
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
    private val bluetoothSource: BluetoothSource,
    private val iapRepo: IAPRepo,
    private val dispatcherProvider: DispatcherProvider
) : BaseViewModel<DiscoverState, DiscoverEvent>(DiscoverState()) {
    
    init {
        observeProVersion()
        loadAvailableDevices()
    }
    
    private fun observeProVersion() {
        launch {
            iapRepo.recheck()
            iapRepo.isProVersion()
                .catch { e ->
                    Timber.e(e, "Failed to observe pro version")
                }
                .collect { isProVersion ->
                    updateState { copy(isProVersion = isProVersion) }
                }
        }
    }
    
    private fun loadAvailableDevices() {
        launch {
            updateState { copy(isLoading = true, error = null) }
            
            try {
                combine(
                    deviceRepository.getAllDevices(),
                    bluetoothSource.pairedDevices()
                ) { managedDevices, pairedDevices ->
                    val managedAddresses = managedDevices.map { it.address }.toSet()
                    val availableDevices = pairedDevices.values
                        .filter { device -> device.address !in managedAddresses }
                        .sortedBy { device ->
                            when (device.address) {
                                FakeSpeakerDevice.ADDR -> 0
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
                Timber.e(e, "Failed to load available devices")
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
                Timber.i("Adding new device: $device")
                deviceRepository.addDevice(
                    address = device.address,
                    name = device.name
                )
                updateState { copy(shouldClose = true) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to add device")
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
            iapRepo.buyProVersion(activity)
            dismissDialog()
        }
    }
    
    private fun dismissDialog() {
        updateState { copy(showUpgradeDialog = false) }
    }
}