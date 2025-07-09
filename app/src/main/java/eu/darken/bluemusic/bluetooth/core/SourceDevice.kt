package eu.darken.bluemusic.bluetooth.core

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Parcelable
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.AudioStream.Id
import kotlinx.parcelize.Parcelize

interface SourceDevice : Parcelable {

    val bluetoothClass: BluetoothClass?
    val name: String?
    val address: String
    val alias: String?
    val label: String

    fun getStreamId(type: AudioStream.Type): Id

    @Parcelize
    data class Event(
            val device: SourceDevice,
            val type: Type
    ) : Parcelable {
        enum class Type {
            CONNECTED, DISCONNECTED
        }

        val address: String
            get() = device.address

        companion object {
            private val TAG = logTag("SourceDevice.Event")

            @JvmStatic fun createEvent(intent: Intent): Event? {
                val bluetoothDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (bluetoothDevice == null) {
                    log(TAG, WARN) { "Intent didn't contain a bluetooth device!" }
                    return null
                }
                val sourceDevice: SourceDevice = SourceDeviceWrapper(bluetoothDevice)
                val actionType: Type = when {
                    BluetoothDevice.ACTION_ACL_CONNECTED == intent.action -> Type.CONNECTED
                    BluetoothDevice.ACTION_ACL_DISCONNECTED == intent.action -> Type.DISCONNECTED
                    else -> {
                        log(TAG, WARN) { "Invalid action: ${intent.action}" }
                        return null
                    }
                }
                try {
                    log(TAG) { "Device: $sourceDevice | Action: $actionType" }
                } catch (e: Exception) {
                    log(TAG, ERROR) { e.asLog() }
                    return null
                }
                return Event(sourceDevice, actionType)
            }
        }
    }
}