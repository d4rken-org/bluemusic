package eu.darken.bluemusic.bluetooth.core

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.annotation.RequiresPermission
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.hasApiLevel
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import kotlinx.parcelize.Parcelize

@Parcelize
data class SourceDeviceWrapper(
    override val address: DeviceAddr,
    val alias: String?,
    val name: String?,
    override val deviceType: SourceDevice.Type,
    override val isConnected: Boolean,
) : SourceDevice {

    override val label: String
        get() = alias ?: name ?: address

    override fun getStreamId(type: AudioStream.Type): AudioStream.Id = when (type) {
        AudioStream.Type.MUSIC -> AudioStream.Id.STREAM_MUSIC
        AudioStream.Type.CALL -> AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE
        AudioStream.Type.RINGTONE -> AudioStream.Id.STREAM_RINGTONE
        AudioStream.Type.NOTIFICATION -> AudioStream.Id.STREAM_NOTIFICATION
        AudioStream.Type.ALARM -> AudioStream.Id.STREAM_ALARM
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as SourceDeviceWrapper
        return address == that.address
    }

    override fun hashCode(): Int = address.hashCode()

    override fun toString(): String = "Device(name=$name, address=$address, isConnected=$isConnected)"

    companion object {
        private val TAG = logTag("Bluetooth", "SourceDevice")

        @RequiresPermission(anyOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH])
        fun from(realDevice: BluetoothDevice, isConnected: Boolean) = SourceDeviceWrapper(
            address = realDevice.address,
            alias = run {
                if (hasApiLevel(30)) {
                    @SuppressLint("NewApi")
                    realDevice.alias
                } else {
                    try {
                        val method = realDevice.javaClass.getMethod("getAliasName")
                        method.invoke(realDevice) as String?
                    } catch (e: Exception) {
                        log(TAG, ERROR) { "Failed to use getAliasName(): ${e.message}" }
                        null
                    }
                }
            },
            name = realDevice.name,
            deviceType = realDevice.bluetoothClass.toType(),
            isConnected = isConnected
        )
    }
}