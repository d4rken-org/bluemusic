package eu.darken.bluemusic;

import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.content.BroadcastReceiver;

import com.bugsnag.android.Bugsnag;
import com.bugsnag.android.Client;

import javax.inject.Inject;

import eu.darken.bluemusic.util.BugsnagErrorHandler;
import eu.darken.bluemusic.util.BugsnagTree;
import eu.darken.ommvplib.injection.ComponentSource;
import eu.darken.ommvplib.injection.ManualInjector;
import eu.darken.ommvplib.injection.activity.HasManualActivityInjector;
import eu.darken.ommvplib.injection.broadcastreceiver.HasManualBroadcastReceiverInjector;
import eu.darken.ommvplib.injection.service.HasManualServiceInjector;
import io.realm.Realm;
import io.realm.RealmConfiguration;
import timber.log.Timber;


public class App extends Application implements HasManualActivityInjector, HasManualBroadcastReceiverInjector, HasManualServiceInjector {

    @Inject BugsnagTree bugsnagTree;
    @Inject BugsnagErrorHandler errorHandler;
    @Inject AppComponent appComponent;
    @Inject ComponentSource<Activity> activityInjector;
    @Inject ComponentSource<BroadcastReceiver> receiverInjector;
    @Inject ComponentSource<Service> serviceInjector;

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) Timber.plant(new Timber.DebugTree());

        Realm.init(this);
        RealmConfiguration realmConfig = new RealmConfiguration.Builder()
                .schemaVersion(2)
//                .deleteRealmIfMigrationNeeded()
                .build();
        Realm.setDefaultConfiguration(realmConfig);

        DaggerAppComponent.builder()
                .androidModule(new AndroidModule(this))
                .build()
                .injectMembers(this);

        Timber.plant(bugsnagTree);
        Client bugsnagClient = Bugsnag.init(this);
        bugsnagClient.beforeNotify(errorHandler);

        Timber.d("Bugsnag setup done!");
    }

    @Override
    public ManualInjector<Activity> activityInjector() {
        return activityInjector;
    }

    @Override
    public ManualInjector<BroadcastReceiver> broadcastReceiverInjector() {
        return receiverInjector;
    }

    @Override
    public ManualInjector<Service> serviceInjector() {
        return serviceInjector;
    }
}
