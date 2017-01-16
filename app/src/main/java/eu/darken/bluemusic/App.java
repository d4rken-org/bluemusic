package eu.darken.bluemusic;

import android.app.Application;

import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;

import eu.darken.bluemusic.util.AndroidModule;
import io.realm.Realm;
import io.realm.RealmConfiguration;
import timber.log.Timber;


public class App extends Application {
    public static final String LOGPREFIX = "TMP:";
    private static RefWatcher refWatcher;
    private int theme = 0;

    public static RefWatcher getRefWatcher() {
        return refWatcher;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) Timber.plant(new Timber.DebugTree());
        refWatcher = LeakCanary.install(this);
        Injector.INSTANCE.init(this);
    }

    public static String prefixTag(String postfix) {
        return LOGPREFIX + postfix;
    }


    public enum Injector {
        INSTANCE;
        AppComponent appComponent;

        Injector() {
        }

        void init(App app) {
            RealmConfiguration realmConfig = new RealmConfiguration.Builder(app, app.getCacheDir())
                    .deleteRealmIfMigrationNeeded()
                    .build();
            Realm.setDefaultConfiguration(realmConfig);
            appComponent = DaggerAppComponent.builder()
                    .androidModule(new AndroidModule(app))
                    .build();
        }

        public AppComponent getAppComponent() {
            return appComponent;
        }
    }
}
