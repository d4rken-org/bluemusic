package eu.darken.bluemusic

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import eu.darken.bluemusic.data.migration.RealmToRoomMigrator
import eu.darken.bluemusic.main.core.database.MigrationTool
import eu.darken.bluemusic.settings.core.Settings
import eu.darken.bluemusic.util.ApiHelper
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import timber.log.Timber.DebugTree
import javax.inject.Inject

class App : Application() {

    lateinit var appComponent: AppComponent

    @Inject lateinit var settings: Settings
    @Inject lateinit var realmToRoomMigrator: RealmToRoomMigrator
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(DebugTree())

        appComponent = DaggerAppComponent.builder()
                .application(this)
                .build()
        appComponent.inject(this)

        val migrationTool = MigrationTool()
        Realm.init(this)
        val realmConfig = RealmConfiguration.Builder()
                .schemaVersion(migrationTool.schemaVersion.toLong())
                .migration(migrationTool.migration)
                .build()
        Realm.setDefaultConfiguration(realmConfig)
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread: Thread, error: Throwable ->
            Timber.e(error, "$thread threw and uncaught exception")
            originalHandler?.uncaughtException(thread, error)
        }

        if (ApiHelper.hasAndroid12() && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            settings.isShowOnboarding = true
        }
        
        // Perform data migration from Realm to Room
        applicationScope.launch {
            try {
                val migrationSuccess = realmToRoomMigrator.migrate()
                if (migrationSuccess) {
                    Timber.d("Data migration completed successfully")
                    // Clean up Realm files after successful migration
                    realmToRoomMigrator.cleanupRealmFiles()
                }
            } catch (e: Exception) {
                Timber.e(e, "Data migration failed")
            }
        }

        Timber.d("App onCreate() done!")
    }
}