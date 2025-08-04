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
import eu.darken.bluemusic.common.datastore.valueBlocking
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
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.worker.BluetoothEventQueue
import eu.darken.bluemusic.monitor.core.worker.MonitorControl
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

        if (!devicesSettings.isEnabled.valueBlocking) {
            log(TAG, INFO) { "We are disabled." }
            return
        }

        val pendingResult = goAsync()

        appScope.launch {
            try {
                handleEvent(intent)
            } catch (e: Exception) {
                log(TAG, ERROR) { "Error handling bluetooth event: ${e.asLog()}" }
            } finally {
                pendingResult.finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleEvent(intent: Intent) {
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
        log(TAG, DEBUG) { "Current devices: $devices" }

        val managedDevice = devices.singleOrNull { device -> device.address == bluetoothDevice.address }
        if (managedDevice == null) {
            log(TAG, DEBUG) { "Event belongs to an un-managed device" }
            return
        }
        log(TAG, DEBUG) { "Event concerns device $managedDevice" }

        monitorControl.startMonitor()

        val actualEvent = BluetoothEventQueue.Event(
            type = eventType,
            sourceDevice = SourceDeviceWrapper.from(
                realDevice = bluetoothDevice,
                isConnected = eventType == BluetoothEventQueue.Event.Type.CONNECTED
            )
        )

        val fakeSpeakerEvent = deviceRepo.getDevice(speakerDeviceProvider.address)?.let { speakerDevice ->
            log(TAG, DEBUG) { "Speaker is a managed device, generating fake event." }
            BluetoothEventQueue.Event(
                type = when (eventType) {
                    BluetoothEventQueue.Event.Type.CONNECTED -> BluetoothEventQueue.Event.Type.DISCONNECTED
                    BluetoothEventQueue.Event.Type.DISCONNECTED -> BluetoothEventQueue.Event.Type.CONNECTED
                },
                sourceDevice = speakerDeviceProvider.getSpeaker(
                    isConnected = eventType == BluetoothEventQueue.Event.Type.DISCONNECTED
                ),
            )
        }

        when (eventType) {
            BluetoothEventQueue.Event.Type.CONNECTED -> {
                log(TAG, INFO) { "Sending speaker disconnect first, then device connect." }
                fakeSpeakerEvent?.let { eventQueue.submit(it) }
                eventQueue.submit(actualEvent)
            }

            BluetoothEventQueue.Event.Type.DISCONNECTED -> {
                log(TAG, INFO) { "Sending real device event first, then fake speaker connect." }
                eventQueue.submit(actualEvent)
                fakeSpeakerEvent?.let { eventQueue.submit(it) }
            }
        }

        delay(10 * 1000L)
    }

    companion object {
        private val TAG = logTag("Monitor", "Event", "Receiver")
    }
}