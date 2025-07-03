package eu.darken.bluemusic.data.device

import eu.darken.bluemusic.main.core.database.ManagedDevice
import kotlinx.coroutines.flow.Flow

interface DeviceManagerFlow {
    fun devices(): Flow<Map<String, ManagedDevice>>
    suspend fun getDevice(address: String): ManagedDevice?
    suspend fun updateDevice(device: ManagedDevice)
}