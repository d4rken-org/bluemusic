package eu.darken.bluemusic

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.BroadcastReceiver
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.Configuration
import eu.darken.bluemusic.main.core.database.MigrationTool
import eu.darken.bluemusic.settings.core.Settings
import eu.darken.bluemusic.util.BugsnagErrorHandler
import eu.darken.bluemusic.util.BugsnagTree
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

    @Inject lateinit var bugsnagTree: BugsnagTree
    @Inject lateinit var errorHandler: BugsnagErrorHandler
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

        Timber.plant(bugsnagTree)

        Configuration.load(this).apply {
            addOnError(errorHandler)
            apiKey = "test"
        }.let { Bugsnag.start(this@App, it) }

        Timber.d("Bugsnag setup done!")
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
        Timber.d("App onCreate() done!")
    }

    override fun activityInjector(): ManualInjector<Activity> = activityInjector

    override fun broadcastReceiverInjector(): ManualInjector<BroadcastReceiver> = receiverInjector

    override fun serviceInjector(): ManualInjector<Service> = serviceInjector
}