package eu.darken.bluemusic.devices.core

import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.devices.core.database.DeviceDatabase
import eu.darken.bluemusic.devices.core.database.legacy.RealmToRoomMigrator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val deviceDatabase: DeviceDatabase,
    private val dispatcherProvider: DispatcherProvider,
    private val migrator: RealmToRoomMigrator
) {

    companion object {
        private val TAG = logTag("DeviceRepository")
    }

    suspend fun ensureMigration() {
        withContext(dispatcherProvider.IO) {
            if (migrator.migrate()) {
                migrator.cleanupRealmFiles()
            }
        }
    }

    fun getAllDevices(): Flow<List<DeviceConfigEntity>> {
        return deviceDatabase.devices.getAllDevices()
            .flowOn(dispatcherProvider.IO)
    }

    fun observeDevice(address: String): Flow<DeviceConfigEntity?> {
        return deviceDatabase.devices.observeDevice(address)
            .flowOn(dispatcherProvider.IO)
    }

    suspend fun getDevice(address: String): DeviceConfigEntity? {
        return withContext(dispatcherProvider.IO) {
            deviceDatabase.devices.getDevice(address)
        }
    }

    suspend fun createDevice(address: String): DeviceConfigEntity {
        return withContext(dispatcherProvider.IO) {
            val device = DeviceConfigEntity(
                address = address,
                lastConnected = System.currentTimeMillis()
            )
            deviceDatabase.devices.insertDevice(device)
            log(TAG) { "Created new device config: $address" }
            device
        }
    }

    suspend fun updateDevice(device: DeviceConfigEntity) {
        withContext(dispatcherProvider.IO) {
            deviceDatabase.devices.updateDevice(device)
            log(TAG) { "Updated device config: ${device.address}" }
        }
    }

    suspend fun setAlias(address: String, alias: String?) {
        log(TAG) { "Setting alias for $address to $alias" }
//        return try {
//            val method = realDevice.javaClass.getMethod("setAlias", String::class.java)
//            method.invoke(realDevice, newAlias) as Boolean
//        } catch (e: Exception) {
//            log(SourceDeviceWrapper.Companion.TAG, ERROR) { "Failed to set alias: ${e.asLog()}" }
//            false
//        }
    }

    suspend fun updateDevice(address: String, update: (DeviceConfigEntity) -> DeviceConfigEntity) {
        withContext(dispatcherProvider.IO) {
            val device = deviceDatabase.devices.getDevice(address)
            if (device != null) {
                val updated = update(device)
                deviceDatabase.devices.updateDevice(updated)
                log(TAG) { "Updated device config: $address" }
            } else {
                log(TAG, WARN) { "Device not found for update: $address" }
            }
        }
    }

    suspend fun deleteDevice(address: String) {
        withContext(dispatcherProvider.IO) {
            deviceDatabase.devices.deleteByAddress(address)
            log(TAG) { "Deleted device config: $address" }
        }
    }

    suspend fun updateLastConnected(address: String) {
        withContext(dispatcherProvider.IO) {
            deviceDatabase.devices.updateLastConnected(address, System.currentTimeMillis())
            log(TAG) { "Updated last connected for: $address" }
        }
    }

    suspend fun isDeviceManaged(address: String): Boolean {
        return withContext(dispatcherProvider.IO) {
            deviceDatabase.devices.getDevice(address) != null
        }
    }
}