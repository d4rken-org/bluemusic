package eu.darken.bluemusic.common

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppTool @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getApps(): List<Item> {
        val installedPackages = context.packageManager.getInstalledPackages(0)
        return installedPackages.mapNotNull { pkg ->
            try {
                val appName = getLabel(context, pkg.packageName)
                val appIcon = try {
                    getIcon(context, pkg.packageName)
                } catch (e: PackageManager.NameNotFoundException) {
                    log(TAG, ERROR) { "Failed to get app icon for ${pkg.packageName}: ${e.asLog()}" }
                    null
                }
                Item(
                    pkgName = pkg.packageName,
                    appName = appName,
                    appIcon = appIcon
                )
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to get app info for ${pkg.packageName}: ${e.asLog()}" }
                null
            }
        }.sortedBy { it.appName }
    }

    fun launch(pkg: String) {
        var intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            intent = tryGetLauncherIntent(pkg)
            log(TAG) { "No default launch intent, was launcher=${intent != null}" }
        }
        if (intent == null) {
            intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$pkg")
            }
            log(TAG) { "No default launch intent, default to opening Google Play" }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        log(TAG, INFO) { "Launching: $intent" }
        context.startActivity(intent)
    }

    private fun tryGetLauncherIntent(pkg: String): Intent? {
        if (!getLauncherPkgs().contains(pkg)) return null
        return Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
    }

    private fun getLauncherPkgs(): Collection<String> {
        val launchers = mutableSetOf<String>()
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        for (info in context.packageManager.queryIntentActivities(mainIntent, 0)) {
            info.activityInfo?.packageName?.let { launchers.add(it) }
        }
        return launchers
    }

    data class Item(
        val pkgName: String,
        val appName: String,
        val appIcon: Drawable? = null
    )

    companion object {
        private val TAG = logTag("AppTool")

        @JvmStatic
        @Throws(PackageManager.NameNotFoundException::class)
        fun getLabel(context: Context, pkg: String): String {
            val applicationInfo = context.packageManager.getApplicationInfo(pkg, 0)
            return applicationInfo.loadLabel(context.packageManager).toString()
        }

        @JvmStatic
        @Throws(PackageManager.NameNotFoundException::class)
        fun getIcon(context: Context, pkg: String): Drawable {
            return context.packageManager.getApplicationIcon(pkg)
        }
    }
}