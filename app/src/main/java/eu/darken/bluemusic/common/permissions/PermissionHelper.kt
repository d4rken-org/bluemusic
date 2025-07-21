package eu.darken.bluemusic.common.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.BuildConfig
import eu.darken.bluemusic.common.hasApiLevel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class PermissionHint(
        val shouldShow: Boolean,
        val intent: Intent? = null
    )

    private val powerManager by lazy { context.getSystemService(Context.POWER_SERVICE) as PowerManager }

    fun hasBluetoothPermission(): Boolean = if (hasApiLevel(Build.VERSION_CODES.S)) {
        hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        hasPermission(Manifest.permission.BLUETOOTH)
    }

    fun getBluetoothPermission(): String = if (hasApiLevel(Build.VERSION_CODES.S)) {
        Manifest.permission.BLUETOOTH_CONNECT
    } else {
        Manifest.permission.BLUETOOTH
    }

    fun hasNotificationPermission(): Boolean = if (hasApiLevel(Build.VERSION_CODES.TIRAMISU)) {
        hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        // Prior to Android 13, notification permission is granted by default
        true
    }

    fun getNotificationPermission(): String? = if (hasApiLevel(Build.VERSION_CODES.TIRAMISU)) {
        Manifest.permission.POST_NOTIFICATIONS
    } else {
        null
    }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasPermissions(vararg permissions: String): Boolean = permissions.all { hasPermission(it) }

    fun getMissingPermissions(vararg permissions: String): List<String> = permissions.filter { !hasPermission(it) }

    fun isIgnoringBatteryOptimizations(): Boolean = if (hasApiLevel(Build.VERSION_CODES.M)) {
        powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)
    } else {
        // Battery optimization doesn't exist before Android 6.0
        true
    }

    fun needsBatteryOptimization(): Boolean = hasApiLevel(Build.VERSION_CODES.O) && !isIgnoringBatteryOptimizations()

    fun canDrawOverlays(): Boolean = if (hasApiLevel(Build.VERSION_CODES.M)) {
        Settings.canDrawOverlays(context)
    } else {
        // Overlay permission doesn't exist before Android 6.0
        true
    }

    fun needsOverlayPermission(): Boolean = hasApiLevel(Build.VERSION_CODES.Q) && !canDrawOverlays()

    fun getBatteryOptimizationHint(isDismissed: Boolean): PermissionHint {
        val shouldShow = hasApiLevel(Build.VERSION_CODES.O) && needsBatteryOptimization() && !isDismissed
        return if (shouldShow) {
            val intent = Intent().apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            }
            PermissionHint(shouldShow = true, intent = intent)
        } else {
            PermissionHint(shouldShow = false)
        }
    }

    fun getOverlayPermissionHint(isDismissed: Boolean, hasDevicesNeedingOverlay: Boolean): PermissionHint {
        val shouldShow = hasApiLevel(Build.VERSION_CODES.Q) &&
                hasDevicesNeedingOverlay &&
                needsOverlayPermission() &&
                !isDismissed
        return if (shouldShow) {
            val intent = Intent().apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                action = Settings.ACTION_MANAGE_OVERLAY_PERMISSION
            }
            PermissionHint(shouldShow = true, intent = intent)
        } else {
            PermissionHint(shouldShow = false)
        }
    }

    fun getNotificationPermissionHint(isDismissed: Boolean): PermissionHint {
        val shouldShow = hasApiLevel(Build.VERSION_CODES.TIRAMISU) &&
                !hasNotificationPermission() &&
                !isDismissed
        return PermissionHint(shouldShow = shouldShow)
    }
}