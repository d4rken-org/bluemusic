package eu.darken.bluemusic.data.device

import eu.darken.bluemusic.AppComponent
import eu.darken.bluemusic.common.coroutines.DispatcherProvider
import eu.darken.bluemusic.data.migration.RealmToRoomMigrator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@AppComponent.Scope
class DeviceRepository @Inject constructor(
    private val deviceConfigDao: DeviceConfigDao,
    private val dispatcherProvider: DispatcherProvider,
    private val migrator: RealmToRoomMigrator
) {
    
    suspend fun ensureMigration() {
        withContext(dispatcherProvider.io) {
            if (migrator.migrate()) {
                migrator.cleanupRealmFiles()
            }
        }
    }
    
    fun getAllDevices(): Flow<List<DeviceConfigEntity>> {
        return deviceConfigDao.getAllDevices()
            .flowOn(dispatcherProvider.io)
    }
    
    fun observeDevice(address: String): Flow<DeviceConfigEntity?> {
        return deviceConfigDao.observeDevice(address)
            .flowOn(dispatcherProvider.io)
    }
    
    suspend fun getDevice(address: String): DeviceConfigEntity? {
        return withContext(dispatcherProvider.io) {
            deviceConfigDao.getDevice(address)
        }
    }
    
    suspend fun createDevice(address: String): DeviceConfigEntity {
        return withContext(dispatcherProvider.io) {
            val device = DeviceConfigEntity(
                address = address,
                lastConnected = System.currentTimeMillis()
            )
            deviceConfigDao.insertDevice(device)
            Timber.d("Created new device config: $address")
            device
        }
    }
    
    suspend fun updateDevice(device: DeviceConfigEntity) {
        withContext(dispatcherProvider.io) {
            deviceConfigDao.updateDevice(device)
            Timber.d("Updated device config: ${device.address}")
        }
    }
    
    suspend fun updateDevice(address: String, update: (DeviceConfigEntity) -> DeviceConfigEntity) {
        withContext(dispatcherProvider.io) {
            val device = deviceConfigDao.getDevice(address)
            if (device != null) {
                val updated = update(device)
                deviceConfigDao.updateDevice(updated)
                Timber.d("Updated device config: $address")
            } else {
                Timber.w("Device not found for update: $address")
            }
        }
    }
    
    suspend fun deleteDevice(address: String) {
        withContext(dispatcherProvider.io) {
            deviceConfigDao.deleteByAddress(address)
            Timber.d("Deleted device config: $address")
        }
    }
    
    suspend fun updateLastConnected(address: String) {
        withContext(dispatcherProvider.io) {
            deviceConfigDao.updateLastConnected(address, System.currentTimeMillis())
            Timber.d("Updated last connected for: $address")
        }
    }
    
    suspend fun isDeviceManaged(address: String): Boolean {
        return withContext(dispatcherProvider.io) {
            deviceConfigDao.getDevice(address) != null
        }
    }
}