package eu.darken.bluemusic.bluetooth.ui.discover

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.SingleEventFlow
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.devices.core.DeviceRepo
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val deviceRepo: DeviceRepo,
    private val bluetoothSource: BluetoothRepo,
    private val upgradeRepo: UpgradeRepo,
    private val dispatcherProvider: DispatcherProvider,
    private val navCtrl: NavigationController,
) : ViewModel4(dispatcherProvider, logTag("Bluetooth", "Discover", "VM"), navCtrl) {

    val events = SingleEventFlow<DiscoverEvent>()

    val state = combine(
        bluetoothSource.pairedDevices.filterNotNull(),
        deviceRepo.devices,
        upgradeRepo.upgradeInfo,
    ) { paired, managed, upgradeInfo ->
        State(
            devices = paired.filterNot { p -> managed.any { p.address == it.address } },
            managedDeviceCount = managed.size,
            isProVersion = upgradeInfo.isUpgraded,
        )
    }.asStateFlow()

    data class State(
        val devices: List<SourceDevice> = emptyList(),
        val isLoading: Boolean = false,
        val isProVersion: Boolean = false,
        val managedDeviceCount: Int = 0,
        val error: String? = null,
        val showUpgradeDialog: Boolean = false,
        val shouldClose: Boolean = false
    )

    fun onDeviceSelected(device: SourceDevice) {
        log(tag) { "Device selected: $device" }
        launch {
            val currentState = state.first()

            if (!currentState.isProVersion && currentState.managedDeviceCount >= 2) {
                events.emit(DiscoverEvent.RequiresUpgrade)
            } else {
                deviceRepo.createDevice(device.address)
                navCtrl.up()
            }
        }
    }

}
