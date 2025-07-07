package eu.darken.bluemusic.data.device

import eu.darken.bluemusic.AppComponent
import eu.darken.bluemusic.common.coroutines.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AppComponent.Scope
class DeviceManagerFlowAdapter @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val dispatcherProvider: DispatcherProvider
) : DeviceManagerFlow {
    
    override fun devices(): Flow<Map<String, ManagedDevice>> {
        return deviceRepository.getAllDevices()
            .map { entities ->
                entities.associate { entity ->
                    entity.address to entity.toManagedDevice()
                }
            }
            .flowOn(dispatcherProvider.io)
    }
    
    override suspend fun getDevice(address: String): ManagedDevice? {
        return withContext(dispatcherProvider.io) {
            deviceRepository.getDevice(address)?.toManagedDevice()
        }
    }
    
    override suspend fun updateDevice(device: ManagedDevice) {
        withContext(dispatcherProvider.io) {
            val entity = deviceRepository.getDevice(device.address) ?: return@withContext
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
            deviceRepository.updateDevice(updated)
        }
    }
    
    private fun DeviceConfigEntity.toManagedDevice(): ManagedDevice {
        val device = ManagedDevice(address, name)
        device.alias = alias
        device.lastConnected = lastConnected
        device.actionDelay = actionDelay
        device.adjustmentDelay = adjustmentDelay
        device.monitoringDuration = monitoringDuration
        device.volumeLock = volumeLock
        device.keepAwake = keepAwake
        device.nudgeVolume = nudgeVolume
        device.autoplay = autoplay
        device.launchPkg = launchPkg
        
        // Set volume configurations
        device.musicVolume = musicVolume
        device.callVolume = callVolume
        device.ringVolume = ringVolume
        device.notificationVolume = notificationVolume
        device.alarmVolume = alarmVolume
        
        return device
    }
}