package eu.darken.bluemusic.bluetooth.ui

import android.bluetooth.BluetoothClass
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Bluetooth
import androidx.compose.material.icons.twotone.Computer
import androidx.compose.material.icons.twotone.DirectionsCar
import androidx.compose.material.icons.twotone.Headphones
import androidx.compose.material.icons.twotone.Headset
import androidx.compose.material.icons.twotone.Smartphone
import androidx.compose.material.icons.twotone.Speaker
import androidx.compose.material.icons.twotone.Watch
import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag

/**
 * Maps Bluetooth device classes to appropriate Material Design icons
 */
object DeviceIconMapper {

    private val TAG = logTag("DeviceIconMapper")

    /**
     * Returns an appropriate icon for the given source device based on its Bluetooth class
     */
    fun getIconForDevice(device: SourceDevice): ImageVector {
        val bluetoothClass = device.bluetoothClass
        if (bluetoothClass == null) {
            log(TAG) { "No BluetoothClass for device: ${device.label} (${device.address})" }
            return Icons.TwoTone.Bluetooth
        }

        log(TAG) {
            "Device: ${device.label} - Class: ${bluetoothClass.deviceClass} (0x${Integer.toHexString(bluetoothClass.deviceClass)}), Major: ${bluetoothClass.majorDeviceClass} (0x${
                Integer.toHexString(
                    bluetoothClass.majorDeviceClass
                )
            })"
        }

        val icon = when {
            // Check specific device class first (combination of major and minor)
            isDeviceClass(bluetoothClass, BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES) -> Icons.TwoTone.Headphones
            isDeviceClass(bluetoothClass, BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO) -> Icons.TwoTone.DirectionsCar
            isDeviceClass(bluetoothClass, BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER) -> Icons.TwoTone.Speaker
            isDeviceClass(bluetoothClass, BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET) -> Icons.TwoTone.Headset
            isDeviceClass(bluetoothClass, BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO) -> Icons.TwoTone.Speaker
            isDeviceClass(bluetoothClass, BluetoothClass.Device.COMPUTER_LAPTOP) -> Icons.TwoTone.Computer
            isDeviceClass(bluetoothClass, BluetoothClass.Device.COMPUTER_DESKTOP) -> Icons.TwoTone.Computer
            isDeviceClass(bluetoothClass, BluetoothClass.Device.PHONE_SMART) -> Icons.TwoTone.Smartphone
            isDeviceClass(bluetoothClass, BluetoothClass.Device.PHONE_CELLULAR) -> Icons.TwoTone.Smartphone
            isDeviceClass(bluetoothClass, BluetoothClass.Device.WEARABLE_WRIST_WATCH) -> Icons.TwoTone.Watch

            // Fallback to major class if specific device class doesn't match
            isMajorDeviceClass(bluetoothClass, BluetoothClass.Device.Major.AUDIO_VIDEO) -> Icons.TwoTone.Headphones
            isMajorDeviceClass(bluetoothClass, BluetoothClass.Device.Major.COMPUTER) -> Icons.TwoTone.Computer
            isMajorDeviceClass(bluetoothClass, BluetoothClass.Device.Major.PHONE) -> Icons.TwoTone.Smartphone
            isMajorDeviceClass(bluetoothClass, BluetoothClass.Device.Major.WEARABLE) -> Icons.TwoTone.Watch

            // Default icon for unknown device types
            else -> Icons.TwoTone.Bluetooth
        }

        log(TAG) { "Selected icon for ${device.label}: ${icon.name}" }
        return icon
    }

    /**
     * Checks if the bluetooth class matches a specific device class constant
     */
    private fun isDeviceClass(bluetoothClass: BluetoothClass, deviceClass: Int): Boolean {
        return bluetoothClass.deviceClass == deviceClass
    }

    /**
     * Checks if the bluetooth class matches a major device class
     */
    private fun isMajorDeviceClass(bluetoothClass: BluetoothClass, majorDeviceClass: Int): Boolean {
        return bluetoothClass.majorDeviceClass == majorDeviceClass
    }
}