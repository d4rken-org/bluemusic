package eu.darken.bluemusic

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import eu.darken.bluemusic.common.BuildConfigWrap
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.DebugSettings
import eu.darken.bluemusic.common.debug.logging.LogCatLogger
import eu.darken.bluemusic.common.debug.logging.Logging
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.database.legacy.MigrationTool
import eu.darken.bluemusic.legacy.LegacyMigration
import eu.darken.bluemusic.main.core.CurriculumVitae
import eu.darken.bluemusic.main.core.GeneralSettings
import eu.darken.bluemusic.monitor.core.audio.StreamHelper
import eu.darken.bluemusic.monitor.core.worker.MonitorControl
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var dispatcherProvider: DispatcherProvider
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var generalSettings: GeneralSettings
    @Inject lateinit var debugSettings: DebugSettings
    @Inject lateinit var curriculumVitae: CurriculumVitae
    @Inject lateinit var monitorControl: MonitorControl
    @Inject lateinit var streamHelper: StreamHelper

    @Inject lateinit var legacyMigration: LegacyMigration


    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Logging.install(LogCatLogger())

        appScope.launch {
            curriculumVitae.updateAppLaunch()
        }

        val migrationTool = MigrationTool()
        Realm.init(this)
        val realmConfig = RealmConfiguration.Builder()
            .schemaVersion(migrationTool.schemaVersion.toLong())
            .migration(migrationTool.migration)
            .build()
        Realm.setDefaultConfiguration(realmConfig)

        legacyMigration.migration()


        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log(TAG, ERROR) { "UNCAUGHT EXCEPTION: ${throwable.asLog()}" }
            if (oldHandler != null) oldHandler.uncaughtException(thread, throwable) else exitProcess(1)
            Thread.sleep(100)
        }

//        appScope.launch {
//            while (currentCoroutineContext().isActive) {
//                val volumes = mutableListOf<Pair<AudioStream.Id, Float>>()
//                AudioStream.Id.entries.forEach { id ->
//
//                    val curVol = streamHelper.getVolumePercentage(id)
//                    volumes.add(id to curVol)
//                }
//                log(TAG, DEBUG) { "Volumes: ${volumes.joinToString(", ")}" }
//                delay(100)
//            }
//        }

        appScope.launch {
            monitorControl.startMonitor(forceStart = true)
        }

        log(TAG) { "onCreate() done! ${Exception().asLog()}" }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(
                when {
                    BuildConfigWrap.DEBUG -> android.util.Log.VERBOSE
                    BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.DEV -> android.util.Log.DEBUG
                    BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.BETA -> android.util.Log.INFO
                    BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.RELEASE -> android.util.Log.WARN
                    else -> android.util.Log.VERBOSE
                }
            )
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        private val TAG = logTag("App")
    }
}