package eu.darken.bluemusic.common

import android.content.Context
import android.content.pm.PackageInfo


fun Context.getPackageInfo(): PackageInfo = packageManager.getPackageInfo(packageName, 0)