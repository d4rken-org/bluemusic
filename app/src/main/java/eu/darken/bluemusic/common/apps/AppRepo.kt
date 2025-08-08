package eu.darken.bluemusic.common.apps

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepo @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
) {

    private val _apps = MutableStateFlow<Set<AppInfo>>(emptySet())
    val apps: Flow<Set<AppInfo>> = _apps.asStateFlow()

    init {
        appScope.launch {
            loadApps()
        }
    }

    private fun loadApps() {
        log(TAG) { "Loading apps with MAIN/LAUNCHER intent..." }

        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolvedActivities = context.packageManager.queryIntentActivities(mainIntent, 0)
        val appInfos = resolvedActivities
            .mapNotNull { resolveInfo ->
                try {
                    val packageName = resolveInfo.activityInfo.packageName
                    val label = resolveInfo.loadLabel(context.packageManager).toString()
                    val icon = try {
                        resolveInfo.loadIcon(context.packageManager)
                    } catch (e: Exception) {
                        log(TAG, ERROR) { "Failed to load icon for $packageName: ${e.asLog()}" }
                        null
                    }

                    AppInfo(
                        packageName = packageName,
                        label = label,
                        icon = icon
                    )
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Failed to load app info: ${e.asLog()}" }
                    null
                }
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toSet()

        log(TAG) { "Loaded ${appInfos.size} apps" }
        _apps.value = appInfos
    }

    fun launch(pkg: String) {
        var intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            intent = tryGetLauncherIntent(pkg)
            log(TAG) { "No default launch intent, was launcher=${intent != null}" }
        }
        if (intent == null) {
            intent = Intent(Intent.ACTION_VIEW).apply {
                data = "market://details?id=$pkg".toUri()
            }
            log(TAG) { "No default launch intent, default to opening Google Play" }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        log(TAG, INFO) { "Launching: $intent" }
        context.startActivity(intent)
    }

    fun goToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        log(TAG, INFO) { "Going to home screen" }
        context.startActivity(homeIntent)
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

    companion object {
        private val TAG = logTag("AppTool")
    }
}