package eu.darken.bluemusic

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.DebugSettings
import eu.darken.bluemusic.common.debug.logging.LogCatLogger
import eu.darken.bluemusic.common.debug.logging.Logging
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.database.legacy.MigrationTool
import eu.darken.bluemusic.devices.core.database.legacy.RealmToRoomMigrator
import eu.darken.bluemusic.main.core.CurriculumVitae
import eu.darken.bluemusic.main.core.GeneralSettings
import eu.darken.bluemusic.main.core.LegacySettings
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltAndroidApp
class App : Application() {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var dispatcherProvider: DispatcherProvider
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var generalSettings: GeneralSettings
    @Inject lateinit var debugSettings: DebugSettings
    @Inject lateinit var curriculumVitae: CurriculumVitae

    @Inject lateinit var legacySettings: LegacySettings
    @Inject lateinit var realmToRoomMigrator: RealmToRoomMigrator


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

        // TODO Migrate LegacySettings

        // Perform data migration from Realm to Room
        appScope.launch {
            try {
                val migrationSuccess = realmToRoomMigrator.migrate()
                if (migrationSuccess) {
                    log(TAG, DEBUG) { "Data migration completed successfully" }
                    // Clean up Realm files after successful migration
                    realmToRoomMigrator.cleanupRealmFiles()
                }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Data migration failed: ${e.asLog()}" }
            }
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