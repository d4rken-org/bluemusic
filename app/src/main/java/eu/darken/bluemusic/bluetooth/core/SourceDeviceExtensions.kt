package eu.darken.bluemusic.bluetooth.core

import android.bluetooth.BluetoothClass
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Bluetooth
import androidx.compose.material.icons.twotone.Computer
import androidx.compose.material.icons.twotone.DirectionsCar
import androidx.compose.material.icons.twotone.Headphones
import androidx.compose.material.icons.twotone.Headset
import androidx.compose.material.icons.twotone.Smartphone
import androidx.compose.material.icons.twotone.Speaker
import androidx.compose.material.icons.twotone.SpeakerPhone
import androidx.compose.material.icons.twotone.Watch
import androidx.compose.ui.graphics.vector.ImageVector

fun BluetoothClass?.toType(): SourceDevice.Type {
    return when {
        this == null -> SourceDevice.Type.UNKNOWN
        // Check specific device class first (combination of major and minor)
        isDeviceClass(BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES) -> SourceDevice.Type.HEADPHONES
        isDeviceClass(BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO) -> SourceDevice.Type.CAR_AUDIO
        isDeviceClass(BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER) -> SourceDevice.Type.PORTABLE_SPEAKER
        isDeviceClass(BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET) -> SourceDevice.Type.HEADSET
        isDeviceClass(BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO) -> SourceDevice.Type.PORTABLE_SPEAKER
        isDeviceClass(BluetoothClass.Device.COMPUTER_LAPTOP) -> SourceDevice.Type.COMPUTER
        isDeviceClass(BluetoothClass.Device.COMPUTER_DESKTOP) -> SourceDevice.Type.COMPUTER
        isDeviceClass(BluetoothClass.Device.PHONE_SMART) -> SourceDevice.Type.SMARTPHONE
        isDeviceClass(BluetoothClass.Device.PHONE_CELLULAR) -> SourceDevice.Type.SMARTPHONE
        isDeviceClass(BluetoothClass.Device.WEARABLE_WRIST_WATCH) -> SourceDevice.Type.WATCH

        // Fallback to major class if specific device class doesn't match
        isMajorDeviceClass(BluetoothClass.Device.Major.AUDIO_VIDEO) -> SourceDevice.Type.HEADPHONES
        isMajorDeviceClass(BluetoothClass.Device.Major.COMPUTER) -> SourceDevice.Type.COMPUTER
        isMajorDeviceClass(BluetoothClass.Device.Major.PHONE) -> SourceDevice.Type.SMARTPHONE
        isMajorDeviceClass(BluetoothClass.Device.Major.WEARABLE) -> SourceDevice.Type.WATCH

        // Default icon for unknown device types
        else -> SourceDevice.Type.UNKNOWN
    }
}

private fun BluetoothClass.isDeviceClass(deviceClass: Int): Boolean = this.deviceClass == deviceClass

private fun BluetoothClass.isMajorDeviceClass(majorDeviceClass: Int): Boolean = this.majorDeviceClass == majorDeviceClass

fun SourceDevice.Type.toIcon(): ImageVector = when (this) {
    SourceDevice.Type.HEADPHONES -> Icons.TwoTone.Headphones
    SourceDevice.Type.UNKNOWN -> Icons.TwoTone.Bluetooth
    SourceDevice.Type.PHONE_SPEAKER -> Icons.TwoTone.SpeakerPhone
    SourceDevice.Type.HEADSET -> Icons.TwoTone.Headset
    SourceDevice.Type.CAR_AUDIO -> Icons.TwoTone.DirectionsCar
    SourceDevice.Type.PORTABLE_SPEAKER -> Icons.TwoTone.Speaker
    SourceDevice.Type.COMPUTER -> Icons.TwoTone.Computer
    SourceDevice.Type.SMARTPHONE -> Icons.TwoTone.Smartphone
    SourceDevice.Type.WATCH -> Icons.TwoTone.Watch
}