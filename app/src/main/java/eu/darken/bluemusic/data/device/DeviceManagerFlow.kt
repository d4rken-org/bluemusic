package eu.darken.bluemusic.data.device

import kotlinx.coroutines.flow.Flow

interface DeviceManagerFlow {
    fun devices(): Flow<Map<String, ManagedDevice>>
    suspend fun getDevice(address: String): ManagedDevice?
    suspend fun updateDevice(device: ManagedDevice)
}