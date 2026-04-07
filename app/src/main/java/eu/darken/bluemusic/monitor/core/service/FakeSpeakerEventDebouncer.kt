package eu.darken.bluemusic.monitor.core.service

import eu.darken.bluemusic.bluetooth.core.speaker.SpeakerDeviceProvider
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.datastore.value
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.currentDevices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeSpeakerEventDebouncer @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val eventQueue: BluetoothEventQueue,
    private val deviceRepo: DeviceRepo,
    private val devicesSettings: DevicesSettings,
    private val speakerDeviceProvider: SpeakerDeviceProvider,
) {

    private val mutex = Mutex()
    private var pendingJob: Job? = null

    suspend fun scheduleFakeSpeakerConnect(event: BluetoothEventQueue.Event, debounce: Duration) {
        mutex.withLock {
            pendingJob?.let {
                log(TAG, INFO) { "Replacing pending fake speaker connect with new schedule." }
                it.cancel()
            }
            log(TAG, INFO) { "Scheduling fake speaker connect (debounce=$debounce): $event" }
            pendingJob = appScope.launch {
                delay(debounce.toMillis())

                if (!devicesSettings.isEnabled.value()) {
                    log(TAG, INFO) { "skipping debounced speaker submit, BVM disabled" }
                    return@launch
                }

                val speakerAddress = speakerDeviceProvider.address
                val realDeviceActive = deviceRepo.currentDevices().any { device ->
                    device.address != speakerAddress && device.isConnected
                }
                if (realDeviceActive) {
                    log(TAG, INFO) { "skipping debounced speaker submit, real device is active" }
                    return@launch
                }

                log(TAG, INFO) { "Submitted debounced fake speaker connect: $event" }
                eventQueue.submit(event)
            }
        }
    }

    suspend fun cancelPendingFakeSpeakerConnect() {
        mutex.withLock {
            val job = pendingJob ?: return@withLock
            log(TAG, INFO) { "Cancelled pending fake speaker connect" }
            job.cancel()
            pendingJob = null
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "Event", "FakeSpeakerDebouncer")
        val DEFAULT_DEBOUNCE: Duration = Duration.ofSeconds(3)
    }
}
