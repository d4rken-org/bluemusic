package eu.darken.bluemusic.devices.core

import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.replayingShare
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.devices.core.database.DeviceDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepo @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val deviceDatabase: DeviceDatabase,
    private val bluetoothRepo: BluetoothRepo,
    private val dispatcherProvider: DispatcherProvider,
) {

    // TODO what if bluetooth is suddenly disabled?
    val devices = combine(
        bluetoothRepo.pairedDevices.retry {
            log(TAG, WARN) { "Error loading paired devices: ${it.asLog()}" }
            delay(1000)
            true
        },
        deviceDatabase.devices.getAllDevices()
    ) { paired, managed ->
        val pairedMap = paired?.associateBy { it.address }
        managed.mapNotNull {
            val paired = pairedMap?.get(it.address) ?: return@mapNotNull null
            ManagedDevice(
                device = paired,
                isActive = paired.isActive,
                config = it,
            )
        }.sortedByDescending { it.isActive }
    }.replayingShare(appScope + dispatcherProvider.IO)

    suspend fun createDevice(address: DeviceAddr) {
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

    suspend fun setAlias(address: DeviceAddr, alias: String?) {
        log(TAG) { "Setting alias for $address to $alias" }
//        return try {
//            val method = realDevice.javaClass.getMethod("setAlias", String::class.java)
//            method.invoke(realDevice, newAlias) as Boolean
//        } catch (e: Exception) {
//            log(SourceDeviceWrapper.Companion.TAG, ERROR) { "Failed to set alias: ${e.asLog()}" }
//            false
//        }
    }

    suspend fun updateDevice(address: DeviceAddr, update: (DeviceConfigEntity) -> DeviceConfigEntity) {
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

    suspend fun deleteDevice(address: DeviceAddr) {
        withContext(dispatcherProvider.IO) {
            deviceDatabase.devices.deleteByAddress(address)
            log(TAG) { "Deleted device config: $address" }
        }
    }

    suspend fun updateLastConnected(address: DeviceAddr) {
        withContext(dispatcherProvider.IO) {
            deviceDatabase.devices.updateLastConnected(address, System.currentTimeMillis())
            log(TAG) { "Updated last connected for: $address" }
        }
    }

    suspend fun isDeviceManaged(address: DeviceAddr): Boolean {
        return withContext(dispatcherProvider.IO) {
            deviceDatabase.devices.getDevice(address) != null
        }
    }

    companion object {
        private val TAG = logTag("DeviceRepository")
    }
}