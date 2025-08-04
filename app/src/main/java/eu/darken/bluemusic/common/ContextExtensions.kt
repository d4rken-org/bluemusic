package eu.darken.bluemusic.common

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo


fun Context.getPackageInfo(): PackageInfo = packageManager.getPackageInfo(packageName, 0)

@SuppressLint("NewApi")
fun Context.startServiceCompat(intent: Intent) {
    if (hasApiLevel(26)) startForegroundService(intent) else startService(intent)
}