package eu.darken.bluemusic.monitor.core.service

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.getDevice
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispatches a [BluetoothEventQueue.Event] to all registered
 * [ConnectionModule]s after applying the fake-speaker safeguard and the
 * per-device type dedup.
 *
 * Extracted from `MonitorService` so the dispatch pipeline can be unit-tested
 * in isolation (same pattern as [FakeSpeakerEventDebouncer]).
 */
@Singleton
class EventDispatcher @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val deviceRepo: DeviceRepo,
    private val connectionModuleMap: Set<@JvmSuppressWildcards ConnectionModule>,
    private val eventTypeDedupTracker: EventTypeDedupTracker,
) {

    suspend fun dispatch(bluetoothEvent: BluetoothEventQueue.Event) {
        log(TAG) { "dispatch: Handling $bluetoothEvent" }
        val managedDevice = deviceRepo.getDevice(bluetoothEvent.sourceDevice.address)

        if (managedDevice == null) {
            log(TAG, WARN) { "dispatch: Can't find managed device for $bluetoothEvent" }
            return
        }

        val isFakeSpeakerEvent = bluetoothEvent.sourceDevice.deviceType == SourceDevice.Type.PHONE_SPEAKER
        if (isFakeSpeakerEvent
            && bluetoothEvent.type == BluetoothEventQueue.Event.Type.CONNECTED
            && !managedDevice.isConnected
        ) {
            log(TAG, INFO) { "Dropping stale fake speaker CONNECTED, speaker is not currently the active device" }
            return
        }

        // Skip duplicate broadcasts with the same type for the same device.
        // Placed *after* the fake-speaker safeguard so early-returns there
        // don't populate the dedup map. See [EventTypeDedupTracker] for the
        // motivating Samsung Galaxy Buds 3 Pro duplicate-broadcast quirk.
        if (!eventTypeDedupTracker.shouldProcess(bluetoothEvent.sourceDevice.address, bluetoothEvent.type)) {
            return
        }

        val deviceEvent = when (bluetoothEvent.type) {
            BluetoothEventQueue.Event.Type.CONNECTED -> DeviceEvent.Connected(managedDevice)
            BluetoothEventQueue.Event.Type.DISCONNECTED -> DeviceEvent.Disconnected(managedDevice)
        }

        // TODO make this a module?
        deviceRepo.updateDevice(managedDevice.address) {
            it.copy(lastConnected = System.currentTimeMillis())
        }

        log(TAG) { "dispatch: Processing event $deviceEvent" }

        val modulesByPriority = connectionModuleMap
            .groupBy { it.priority }
            .toSortedMap()

        for ((priority, modules) in modulesByPriority) {
            log(TAG, VERBOSE) { "dispatch: ${modules.size} modules at priority $priority" }

            coroutineScope {
                modules.map { module ->
                    async(dispatcherProvider.IO) {
                        try {
                            log(TAG, VERBOSE) {
                                "dispatch: ${module.tag} HANDLE-START for $deviceEvent"
                            }
                            module.handle(deviceEvent)
                            log(TAG, VERBOSE) {
                                "dispatch: ${module.tag} HANDLE-STOP for $deviceEvent"
                            }
                        } catch (e: Exception) {
                            log(TAG, ERROR) {
                                "dispatch: Error: ${module.tag} for $deviceEvent: ${e.asLog()}"
                            }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "Event", "Dispatcher")
    }
}
