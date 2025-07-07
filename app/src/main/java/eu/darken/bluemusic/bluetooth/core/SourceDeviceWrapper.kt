package eu.darken.bluemusic.bluetooth.core

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.os.Parcel
import android.os.Parcelable
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.*
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.main.core.audio.AudioStream
import java.util.Locale

internal class SourceDeviceWrapper(
    private val realDevice: BluetoothDevice
) : SourceDevice {

    constructor(parcel: Parcel) : this(
        parcel.readParcelable(BluetoothDevice::class.java.classLoader)!!
    )

    override val label: String
        get() = alias ?: name ?: address

    override val alias: String?
        get() = try {
            val method = realDevice.javaClass.getMethod("getAliasName")
            method.invoke(realDevice) as String?
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to use getAliasName(): ${e.message}" }
            null
        }

    override val name: String?
        get() = realDevice.name

    override val address: String
        get() = realDevice.address

    override val bluetoothClass: BluetoothClass?
        get() = realDevice.bluetoothClass

    override fun getStreamId(type: AudioStream.Type): AudioStream.Id {
        return when (type) {
            AudioStream.Type.MUSIC -> AudioStream.Id.STREAM_MUSIC
            AudioStream.Type.CALL -> AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE
            AudioStream.Type.RINGTONE -> AudioStream.Id.STREAM_RINGTONE
            AudioStream.Type.NOTIFICATION -> AudioStream.Id.STREAM_NOTIFICATION
            AudioStream.Type.ALARM -> AudioStream.Id.STREAM_ALARM
            else -> throw IllegalArgumentException("Unsupported AudioStreamType: $type")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as SourceDeviceWrapper
        return address == that.address
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }

    override fun toString(): String {
        return String.format(Locale.US, "Device(name=%s, address=%s)", name, address)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(realDevice, flags)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object {
        private val TAG = logTag("SourceDeviceWrapper")

        @JvmField
        val CREATOR = object : Parcelable.Creator<SourceDeviceWrapper> {
            override fun createFromParcel(parcel: Parcel): SourceDeviceWrapper {
                return SourceDeviceWrapper(parcel)
            }

            override fun newArray(size: Int): Array<SourceDeviceWrapper?> {
                return arrayOfNulls(size)
            }
        }
    }
}