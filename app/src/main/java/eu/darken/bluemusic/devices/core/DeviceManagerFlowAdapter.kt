package eu.darken.bluemusic.devices.core

import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceManagerFlowAdapter @Inject constructor(
    private val deviceRepo: DeviceRepo,
    private val dispatcherProvider: DispatcherProvider
) {

    fun devices(): Flow<Map<String, ManagedDevice>> {
        return deviceRepo.devices
            .map { entities ->
                entities.associate { entity ->
                    entity.address to entity.toManagedDevice()
                }
            }
            .flowOn(dispatcherProvider.IO)
    }

    suspend fun getDevice(address: String): ManagedDevice? {
        return withContext(dispatcherProvider.IO) {
            deviceRepo.getDevice(address)?.toManagedDevice()
        }
    }

    suspend fun updateDevice(device: ManagedDevice) {
        withContext(dispatcherProvider.IO) {
            val entity = deviceRepo.getDevice(device.address) ?: return@withContext
            val updated = entity.copy(
                lastConnected = device.lastConnected,
                actionDelay = device.actionDelay,
                adjustmentDelay = device.adjustmentDelay,
                monitoringDuration = device.monitoringDuration,
                musicVolume = device.musicVolume,
                callVolume = device.callVolume,
                ringVolume = device.ringVolume,
                notificationVolume = device.notificationVolume,
                alarmVolume = device.alarmVolume,
                volumeLock = device.volumeLock,
                keepAwake = device.keepAwake,
                nudgeVolume = device.nudgeVolume,
                autoplay = device.autoplay,
                launchPkg = device.launchPkg,
                alias = device.alias,
                name = device.name
            )
            deviceRepo.updateDevice(updated)
        }
    }

    private fun DeviceConfigEntity.toManagedDevice(): ManagedDevice = ManagedDevice(
        address = address,
        name = name,

        alias = alias,
        lastConnected = lastConnected,
        actionDelay = actionDelay,
        adjustmentDelay = adjustmentDelay,
        monitoringDuration = monitoringDuration,
        volumeLock = volumeLock,
        keepAwake = keepAwake,
        nudgeVolume = nudgeVolume,
        autoplay = autoplay,
        launchPkg = launchPkg,

        musicVolume = musicVolume,
        callVolume = callVolume,
        ringVolume = ringVolume,
        notificationVolume = notificationVolume,
        alarmVolume = alarmVolume,
    )
}