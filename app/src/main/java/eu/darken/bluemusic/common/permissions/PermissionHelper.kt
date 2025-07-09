package eu.darken.bluemusic.common.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val powerManager by lazy { context.getSystemService(Context.POWER_SERVICE) as PowerManager }

    fun hasBluetoothPermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        hasPermission(Manifest.permission.BLUETOOTH)
    }

    fun getBluetoothPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Manifest.permission.BLUETOOTH_CONNECT
    } else {
        Manifest.permission.BLUETOOTH
    }

    fun hasNotificationPermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        // Prior to Android 13, notification permission is granted by default
        true
    }

    fun getNotificationPermission(): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.POST_NOTIFICATIONS
    } else {
        null
    }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasPermissions(vararg permissions: String): Boolean = permissions.all { hasPermission(it) }

    fun getMissingPermissions(vararg permissions: String): List<String> = permissions.filter { !hasPermission(it) }

    fun isIgnoringBatteryOptimizations(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)
    } else {
        // Battery optimization doesn't exist before Android 6.0
        true
    }

    fun needsBatteryOptimization(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isIgnoringBatteryOptimizations()

    fun canDrawOverlays(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        // Overlay permission doesn't exist before Android 6.0
        true
    }

    fun needsOverlayPermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !canDrawOverlays()
}