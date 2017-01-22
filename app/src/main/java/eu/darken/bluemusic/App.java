package eu.darken.bluemusic;

import android.app.Activity;
import android.app.Application;

import com.bugsnag.android.Bugsnag;
import com.bugsnag.android.Client;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;

import eu.darken.bluemusic.util.BugsnagErrorHandler;
import eu.darken.bluemusic.util.BugsnagTree;
import eu.darken.ommvplib.injection.activity.ActivityComponent;
import eu.darken.ommvplib.injection.activity.ActivityComponentBuilder;
import eu.darken.ommvplib.injection.activity.ActivityComponentBuilderSource;
import io.realm.Realm;
import io.realm.RealmConfiguration;
import timber.log.Timber;


public class App extends Application {

    private static RefWatcher refWatcher;

    public static RefWatcher getRefWatcher() {
        return refWatcher;
    }

    @Inject BugsnagTree bugsnagTree;
    @Inject BugsnagErrorHandler errorHandler;

    @Override
    public void onCreate() {
        Injector.INSTANCE.init(this);
        Injector.INSTANCE.getAppComponent().inject(this);
        super.onCreate();

        if (BuildConfig.DEBUG) Timber.plant(new Timber.DebugTree());

        Timber.plant(bugsnagTree);
        Client bugsnagClient = Bugsnag.init(this);
        bugsnagClient.beforeNotify(errorHandler);

        Timber.d("Bugsnag setup done!");

        refWatcher = LeakCanary.install(this);
    }

    public enum Injector implements ActivityComponentBuilderSource {
        INSTANCE;
        @Inject AppComponent appComponent;
        @Inject Map<Class<? extends Activity>, Provider<ActivityComponentBuilder>> componentBuilders;

        Injector() {
        }

        void init(App app) {
            Realm.init(app);
            RealmConfiguration realmConfig = new RealmConfiguration.Builder()
                    .deleteRealmIfMigrationNeeded()
                    .build();
            Realm.setDefaultConfiguration(realmConfig);
            DaggerAppComponent.builder()
                    .androidModule(new AndroidModule(app))
                    .build()
                    .injectMembers(this);
        }

        public AppComponent getAppComponent() {
            return appComponent;
        }

        @Override
        public <ActivityT extends Activity, BuilderT extends ActivityComponentBuilder<ActivityT, ? extends ActivityComponent<ActivityT>>>
        BuilderT getComponentBuilder(Class<ActivityT> activityClass) {
            //noinspection unchecked
            return (BuilderT) componentBuilders.get(activityClass).get();
        }
    }
}
