package eu.darken.bluemusic.bluetooth.core

import android.Manifest
import android.bluetooth.BluetoothDevice
import androidx.annotation.RequiresPermission
import eu.darken.bluemusic.bluetooth.core.BluetoothRepo.Companion.TAG
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log

internal fun BluetoothDevice.isConnected(): Boolean = try {
    val method = this.javaClass.getMethod("isConnected")
    method.invoke(this) as Boolean
} catch (e: Exception) {
    log(TAG, WARN) { "Could not determine connection state for ${this.address}: ${e.asLog()}" }
    false
}

@RequiresPermission(anyOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH])
internal fun BluetoothDevice.hasUUID(target: Int): Boolean = uuids?.any {
    val uuid = it.uuid
    val value = (uuid.mostSignificantBits and 0x0000FFFF00000000L) ushr 32
    value.toInt() == target
} ?: false