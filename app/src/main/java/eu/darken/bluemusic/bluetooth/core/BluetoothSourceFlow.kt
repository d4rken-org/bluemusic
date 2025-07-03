package eu.darken.bluemusic.bluetooth.core

import kotlinx.coroutines.flow.Flow

interface BluetoothSourceFlow {
    val isEnabled: Flow<Boolean>
    val connectedDevices: Flow<Map<String, SourceDevice>>
    
    suspend fun reloadConnectedDevices(): Map<String, SourceDevice>
}