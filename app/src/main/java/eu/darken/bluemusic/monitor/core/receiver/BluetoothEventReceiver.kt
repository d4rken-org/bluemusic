package eu.darken.bluemusic.monitor.core.receiver

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.monitor.core.worker.MonitorControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BluetoothEventReceiver : BroadcastReceiver() {

    @Inject lateinit var monitorControl: MonitorControl
    @Inject @AppScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG) { "onReceive($context, $intent)" }
        if (!EXPECTED_ACTIONS.contains(intent.action)) {
            log(TAG, WARN) { "Unknown action: ${intent.action}" }
            return
        }

        val bluetoothDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        if (bluetoothDevice == null) {
            log(TAG, WARN) { "Event without Bluetooth device association." }
            return
        } else {
            log { "Event related to $bluetoothDevice" }
        }

        val pending = goAsync()
        appScope.launch {
            log(TAG) { "Starting monitor" }
            monitorControl.startMonitor(forceStart = false)
            pending.finish()
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "EventReceiver")
        private val EXPECTED_ACTIONS = setOf(
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothDevice.ACTION_ACL_DISCONNECTED,
        )
    }
}
