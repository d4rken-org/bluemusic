package eu.darken.bluemusic.monitor.core.receiver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.bluemusic.bluetooth.core.SourceDeviceWrapper
import eu.darken.bluemusic.bluetooth.core.speaker.SpeakerDeviceProvider
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.devices.core.getDevice
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.service.BluetoothEventQueue
import eu.darken.bluemusic.monitor.core.service.EventTypeDedupTracker
import eu.darken.bluemusic.monitor.core.service.FakeSpeakerEventDebouncer
import eu.darken.bluemusic.monitor.core.service.MonitorControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MonitorEventReceiver : BroadcastReceiver() {

    @Inject lateinit var devicesSettings: DevicesSettings
    @Inject lateinit var volumeTool: VolumeTool
    @Inject lateinit var speakerDeviceProvider: SpeakerDeviceProvider
    @Inject lateinit var deviceRepo: DeviceRepo
    @Inject lateinit var dispatcherProvider: DispatcherProvider
    @Inject lateinit var eventQueue: BluetoothEventQueue
    @Inject lateinit var eventTypeDedupTracker: EventTypeDedupTracker
    @Inject lateinit var fakeSpeakerEventDebouncer: FakeSpeakerEventDebouncer
    @Inject lateinit var monitorControl: MonitorControl
    @Inject @AppScope lateinit var appScope: CoroutineScope

    private lateinit var context: Context
    private val validActions = listOf(
        BluetoothDevice.ACTION_ACL_CONNECTED,
        BluetoothDevice.ACTION_ACL_DISCONNECTED
    )

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG, VERBOSE) { "onReceive($context, $intent)" }
        this.context = context

        if (!validActions.contains(intent.action ?: "")) {
            log(TAG, ERROR) { "We got called on an invalid intent: $intent" }
            return
        }

        // Capture volume snapshot synchronously before any async processing.
        // At this point (~2ms after broadcast), audio routing hasn't changed
        // yet. By the time the coroutine runs (~80ms+), Android will have
        // rerouted and getStreamVolume() returns the new device's values.
        // Needed for both DISCONNECTED (real device save-on-disconnect) and
        // CONNECTED (the synthetic FakeSpeaker DISCONNECTED side effect).
        val volumeSnapshot = BluetoothEventQueue.VolumeSnapshot(
            levels = AudioStream.Id.entries.associateWith { id ->
                BluetoothEventQueue.VolumeSnapshot.Level(
                    current = volumeTool.getCurrentVolume(id),
                    min = volumeTool.getMinVolume(id),
                    max = volumeTool.getMaxVolume(id),
                )
            }
        )

        val pendingResult = goAsync()

        appScope.launch {
            try {
                val enabledState = devicesSettings.currentEnabledState()
                eventTypeDedupTracker.observeEnabledState(enabledState)

                if (!enabledState.isEnabled) {
                    log(TAG, INFO) { "We are disabled." }
                    return@launch
                }

                val bluetoothDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (bluetoothDevice == null) {
                    log(TAG, WARN) { "Intent didn't contain a bluetooth device!" }
                    return@launch
                }

                if (!deviceRepo.isManaged(bluetoothDevice.address)) {
                    log(TAG, DEBUG) { "Event belongs to an un-managed device" }
                    return@launch
                }

                monitorControl.startMonitor()
                handleEvent(intent, volumeSnapshot)
            } catch (e: Exception) {
                log(TAG, ERROR) { "Error handling bluetooth event: ${e.asLog()}" }
            } finally {
                pendingResult.finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleEvent(intent: Intent, volumeSnapshot: BluetoothEventQueue.VolumeSnapshot?) {
        val bluetoothDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        if (bluetoothDevice == null) {
            log(TAG, WARN) { "Intent didn't contain a bluetooth device!" }
            return
        }

        val eventType = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> BluetoothEventQueue.Event.Type.CONNECTED
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> BluetoothEventQueue.Event.Type.DISCONNECTED
            else -> null
        }
        if (eventType == null) {
            log(TAG) { "Unknown event action = ${intent.action}" }
            return
        }

        val devices = deviceRepo.currentDevices()
        log(TAG, DEBUG) { "Current devices: ${devices.map { "${it.address}/${it.label}(active=${it.isActive})" }}" }

        val managedDevice = devices.singleOrNull { device -> device.address == bluetoothDevice.address }
        if (managedDevice == null) {
            log(TAG, DEBUG) { "Event belongs to an un-managed device" }
            return
        }
        log(TAG, DEBUG) { "Event concerns device ${managedDevice.toCompactString()}" }

        // Read-only pre-filter against duplicate ACL broadcasts (e.g. Samsung Galaxy Buds 3 Pro
        // emits a follow-up ACL_DISCONNECTED ~10s after the first one). Skipping the broadcast
        // here avoids wasted queue submission, debouncer reschedules, and the 10s goAsync delay
        // at the end of this coroutine. IMPORTANT: isDuplicate is read-only; EventDispatcher is
        // the single authoritative committer of dedup state via shouldProcess. Calling
        // shouldProcess here instead would cause the dispatcher's own shouldProcess call to see
        // this update and drop the first-occurrence event, breaking the entire pipeline.
        if (eventTypeDedupTracker.isDuplicate(managedDevice.address, eventType)) {
            log(TAG, INFO) { "Skipping duplicate $eventType broadcast for ${managedDevice.address}" }
            return
        }

        val actualEvent = eventQueue.stampEvent(
            type = eventType,
            sourceDevice = SourceDeviceWrapper.from(
                realDevice = bluetoothDevice,
                isConnected = eventType == BluetoothEventQueue.Event.Type.CONNECTED
            ),
            volumeSnapshot = volumeSnapshot,
        )

        val speakerAddress = speakerDeviceProvider.address
        val otherActiveRealDevices = devices.filter { other ->
            other.isConnected
                    && other.address != bluetoothDevice.address
                    && other.address != speakerAddress
        }
        val speakerStateChanges = otherActiveRealDevices.isEmpty()

        val fakeSpeakerEventType = if (speakerStateChanges) {
            deviceRepo.getDevice(speakerAddress)?.let {
                log(TAG, DEBUG) { "Speaker state will change, generating fake event." }
                when (eventType) {
                    BluetoothEventQueue.Event.Type.CONNECTED -> BluetoothEventQueue.Event.Type.DISCONNECTED
                    BluetoothEventQueue.Event.Type.DISCONNECTED -> BluetoothEventQueue.Event.Type.CONNECTED
                }
            }
        } else {
            log(TAG, DEBUG) { "Other managed devices remain active, no fake speaker event needed." }
            null
        }

        val fakeSpeakerDevice = if (fakeSpeakerEventType != null) {
            speakerDeviceProvider.getSpeaker(
                isConnected = eventType == BluetoothEventQueue.Event.Type.DISCONNECTED
            )
        } else null

        when (eventType) {
            BluetoothEventQueue.Event.Type.CONNECTED -> {
                log(TAG, INFO) { "Sending speaker disconnect first, then device connect." }
                fakeSpeakerEventDebouncer.cancelPendingFakeSpeakerConnect()
                if (fakeSpeakerEventType != null && fakeSpeakerDevice != null) {
                    eventQueue.submit(eventQueue.stampEvent(
                        type = fakeSpeakerEventType,
                        sourceDevice = fakeSpeakerDevice,
                        volumeSnapshot = volumeSnapshot,
                    ))
                }
                eventQueue.submit(actualEvent)
            }

            BluetoothEventQueue.Event.Type.DISCONNECTED -> {
                log(TAG, INFO) { "Sending real device event first, then fake speaker connect." }
                eventQueue.submit(actualEvent)
                if (fakeSpeakerEventType != null && fakeSpeakerDevice != null) {
                    fakeSpeakerEventDebouncer.scheduleFakeSpeakerConnect(
                        sourceDevice = fakeSpeakerDevice,
                        volumeSnapshot = volumeSnapshot,
                        debounce = FakeSpeakerEventDebouncer.DEFAULT_DEBOUNCE,
                    )
                }
            }
        }

        delay(10 * 1000L)
    }

    companion object {
        private val TAG = logTag("Monitor", "Event", "Receiver")
    }
}
