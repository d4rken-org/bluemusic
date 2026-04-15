package eu.darken.bluemusic

import android.app.Application
import android.os.Looper
import dagger.hilt.android.HiltAndroidApp
import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.DebugSettings
import eu.darken.bluemusic.common.debug.logging.LogCatLogger
import eu.darken.bluemusic.common.debug.logging.Logging
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.main.core.CurriculumVitae
import eu.darken.bluemusic.main.core.GeneralSettings
import eu.darken.bluemusic.main.ui.widget.WidgetManager
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.service.MonitorControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltAndroidApp
class App : Application() {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var dispatcherProvider: DispatcherProvider
    @Inject lateinit var generalSettings: GeneralSettings
    @Inject lateinit var debugSettings: DebugSettings
    @Inject lateinit var curriculumVitae: CurriculumVitae
    @Inject lateinit var monitorControl: MonitorControl
    @Inject lateinit var volumeTool: VolumeTool
    @Inject lateinit var widgetManager: WidgetManager
    @Inject lateinit var deviceRepo: DeviceRepo
    @Inject lateinit var upgradeRepo: UpgradeRepo
    @Inject lateinit var bluetoothRepo: BluetoothRepo

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Logging.install(LogCatLogger())

        appScope.launch {
            curriculumVitae.updateAppLaunch()
        }

        widgetManager.syncWidgetPresence()

        appScope.launch {
            widgetManager.hasWidgets
                .drop(1)
                .collect { hasWidgets ->
                    if (!hasWidgets) return@collect
                    widgetManager.refreshWidgets()
                }
        }

        appScope.launch {
            widgetManager.hasWidgets
                .flatMapLatest { hasWidgets ->
                    if (!hasWidgets) return@flatMapLatest emptyFlow()
                    deviceRepo.devices
                        .map { devices ->
                            devices
                                .filter { it.type != SourceDevice.Type.PHONE_SPEAKER }
                                .firstOrNull { it.isActive }
                                ?.let { Triple(it.address, it.label, it.volumeLock) }
                        }
                        .distinctUntilChanged()
                        .drop(1)
                }
                .collect { widgetManager.refreshWidgets() }
        }

        appScope.launch {
            widgetManager.hasWidgets
                .flatMapLatest { hasWidgets ->
                    if (!hasWidgets) return@flatMapLatest emptyFlow()
                    upgradeRepo.upgradeInfo
                        .map { it.isUpgraded }
                        .distinctUntilChanged()
                        .drop(1)
                }
                .collect { widgetManager.refreshWidgets() }
        }

        appScope.launch {
            widgetManager.hasWidgets
                .flatMapLatest { hasWidgets ->
                    if (!hasWidgets) return@flatMapLatest emptyFlow()
                    bluetoothRepo.state
                        .map { it.isEnabled }
                        .distinctUntilChanged()
                        .drop(1)
                }
                .collect { widgetManager.refreshWidgets() }
        }

        var foregroundExceptionHandled = false
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (throwable.isForegroundServiceTimingException() && !foregroundExceptionHandled) {
                foregroundExceptionHandled = true
                log(TAG, WARN) { "Suppressed foreground service timing exception: ${throwable.asLog()}" }
                Looper.loop()
                return@setDefaultUncaughtExceptionHandler
            }
            log(TAG, ERROR) { "UNCAUGHT EXCEPTION: ${throwable.asLog()}" }
            if (oldHandler != null) oldHandler.uncaughtException(thread, throwable) else exitProcess(1)
            Thread.sleep(100)
        }

        log(TAG) { "onCreate() done! ${Exception().asLog()}" }
    }

    companion object {
        private val TAG = logTag("App")

        private fun Throwable.isForegroundServiceTimingException(): Boolean {
            var current: Throwable? = this
            while (current != null) {
                if (current.javaClass.simpleName == "ForegroundServiceDidNotStartInTimeException") return true
                current = current.cause
            }
            return false
        }
    }
}
