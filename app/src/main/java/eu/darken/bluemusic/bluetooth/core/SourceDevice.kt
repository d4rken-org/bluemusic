package eu.darken.bluemusic.bluetooth.core

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Parcelable
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.AudioStream.Id
import kotlinx.parcelize.Parcelize
import timber.log.Timber

interface SourceDevice : Parcelable {

    val bluetoothClass: BluetoothClass?
    val name: String?
    val address: String
    fun setAlias(newAlias: String?): Boolean
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
            @JvmStatic fun createEvent(intent: Intent): Event? {
                val bluetoothDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (bluetoothDevice == null) {
                    Timber.w("Intent didn't contain a bluetooth device!")
                    return null
                }
                val sourceDevice: SourceDevice = SourceDeviceWrapper(bluetoothDevice)
                val actionType: Type = when {
                    BluetoothDevice.ACTION_ACL_CONNECTED == intent.action -> Type.CONNECTED
                    BluetoothDevice.ACTION_ACL_DISCONNECTED == intent.action -> Type.DISCONNECTED
                    else -> {
                        Timber.w("Invalid action: %s", intent.action)
                        return null
                    }
                }
                try {
                    Timber.d("Device: %s | Action: %s", sourceDevice, actionType)
                } catch (e: Exception) {
                    Timber.e(e)
                    return null
                }
                return Event(sourceDevice, actionType)
            }
        }
    }
}