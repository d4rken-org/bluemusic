package eu.darken.bluemusic

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.BroadcastReceiver
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import eu.darken.bluemusic.main.core.database.MigrationTool
import eu.darken.bluemusic.settings.core.Settings
import eu.darken.bluemusic.util.ApiHelper
import eu.darken.mvpbakery.injection.ComponentSource
import eu.darken.mvpbakery.injection.ManualInjector
import eu.darken.mvpbakery.injection.activity.HasManualActivityInjector
import eu.darken.mvpbakery.injection.broadcastreceiver.HasManualBroadcastReceiverInjector
import eu.darken.mvpbakery.injection.service.HasManualServiceInjector
import io.realm.Realm
import io.realm.RealmConfiguration
import timber.log.Timber
import timber.log.Timber.DebugTree
import javax.inject.Inject

class App : Application(), HasManualActivityInjector, HasManualBroadcastReceiverInjector, HasManualServiceInjector {

    @Inject lateinit var appComponent: AppComponent
    @Inject lateinit var activityInjector: ComponentSource<Activity>
    @Inject lateinit var receiverInjector: ComponentSource<BroadcastReceiver>
    @Inject lateinit var serviceInjector: ComponentSource<Service>

    @Inject lateinit var settings: Settings

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(DebugTree())

        DaggerAppComponent.builder()
                .application(this)
                .build()
                .injectMembers(this)

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

        Timber.d("App onCreate() done!")
    }

    override fun activityInjector(): ManualInjector<Activity> = activityInjector

    override fun broadcastReceiverInjector(): ManualInjector<BroadcastReceiver> = receiverInjector

    override fun serviceInjector(): ManualInjector<Service> = serviceInjector
}