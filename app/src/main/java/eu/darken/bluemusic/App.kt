package eu.darken.bluemusic

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.DebugSettings
import eu.darken.bluemusic.common.debug.logging.LogCatLogger
import eu.darken.bluemusic.common.debug.logging.Logging
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.main.core.CurriculumVitae
import eu.darken.bluemusic.main.core.GeneralSettings
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.service.MonitorControl
import kotlinx.coroutines.CoroutineScope
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

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Logging.install(LogCatLogger())

        appScope.launch {
            curriculumVitae.updateAppLaunch()
        }

        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log(TAG, ERROR) { "UNCAUGHT EXCEPTION: ${throwable.asLog()}" }
            if (oldHandler != null) oldHandler.uncaughtException(thread, throwable) else exitProcess(1)
            Thread.sleep(100)
        }

        log(TAG) { "onCreate() done! ${Exception().asLog()}" }
    }

    companion object {
        private val TAG = logTag("App")
    }
}