package eu.darken.bluemusic.common.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Check if the app has Bluetooth permission based on the Android API level
     */
    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.BLUETOOTH)
        }
    }

    /**
     * Get the appropriate Bluetooth permission string based on the Android API level
     */
    fun getBluetoothPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH
        }
    }

    /**
     * Check if the app has notification permission (Android 13+)
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Prior to Android 13, notification permission is granted by default
            true
        }
    }

    /**
     * Get the notification permission string (Android 13+)
     */
    fun getNotificationPermission(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }
    }

    /**
     * Check if a specific permission is granted
     */
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if multiple permissions are granted
     */
    fun hasPermissions(vararg permissions: String): Boolean {
        return permissions.all { hasPermission(it) }
    }

    /**
     * Get a list of permissions that are not granted from the provided list
     */
    fun getMissingPermissions(vararg permissions: String): List<String> {
        return permissions.filter { !hasPermission(it) }
    }
}