package eu.darken.bluemusic.devices.core

import android.annotation.SuppressLint
import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.replayingShare
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.devices.core.database.DeviceDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
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

    val devices = combine(
        bluetoothRepo.state,
        deviceDatabase.devices.getAllDevices()
    ) { btState, managed ->
        val pairedMap = btState.devices?.associateBy { it.address }
        val mappings = managed
            .mapNotNull { config ->
                val paired = pairedMap?.get(config.address) ?: return@mapNotNull null
                config to paired
            }

        mappings.map { (config, paired) ->
            ManagedDevice(
                device = paired,
                isConnected = when (paired.deviceType) {
                    SourceDevice.Type.PHONE_SPEAKER -> mappings
                        .filter { it.second.address != config.address }
                        .none { it.second.isConnected }

                    else -> paired.isConnected
                },
                config = config
            )
        }.sortedByDescending { it.isActive }
    }.replayingShare(appScope + dispatcherProvider.IO)

    @SuppressLint("MissingPermission")
    suspend fun renameDevice(address: DeviceAddr, newName: String?) {
        log(TAG) { "renameDevice: Setting alias for $address to $newName" }
        val targetDevice = getDevice(address) ?: return

        var systemAliasSuccess = false
        if (newName != null && targetDevice.type != SourceDevice.Type.PHONE_SPEAKER) {
            systemAliasSuccess = bluetoothRepo.renameDevice(address, newName)
        }
        log(TAG) { "renameDevice: systemAliasSuccess=$systemAliasSuccess" }

        updateDevice(address) { oldConfig ->
            oldConfig.copy(customName = if (systemAliasSuccess) null else newName)
        }
    }

    suspend fun updateDevice(address: DeviceAddr, update: (DeviceConfigEntity) -> DeviceConfigEntity) {
        withContext(dispatcherProvider.IO) {
            var before = deviceDatabase.devices.getDevice(address)

            if (before == null) {
                log(TAG) { "Device not found for update: $address. Creating new." }
                before = DeviceConfigEntity(address = address)
            }

            val updated = update(before)
            deviceDatabase.devices.updateDevice(updated)

            log(TAG) { "Updated device config: $address" }
        }
    }

    suspend fun deleteDevice(address: DeviceAddr) {
        withContext(dispatcherProvider.IO) {
            deviceDatabase.devices.deleteByAddress(address)
            log(TAG) { "Deleted device config: $address" }
        }
    }

    companion object {
        private val TAG = logTag("Devices", "Repo")
    }
}
