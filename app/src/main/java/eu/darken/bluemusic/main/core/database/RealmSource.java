package eu.darken.bluemusic.main.core.database;

import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

import eu.darken.bluemusic.AppComponent;
import io.reactivex.rxjava3.core.Single;
import io.realm.Realm;
import io.realm.RealmResults;

@AppComponent.Scope
public class RealmSource {
    @Inject
    public RealmSource() {
    }

    public Realm getNewRealmInstance() {
        return Realm.getDefaultInstance();
    }

    public Single<Set<String>> getManagedAddresses() {
        return Single.create(emitter -> {
            Set<String> addressSet = new HashSet<>();

            try (Realm realm = getNewRealmInstance()) {
                final RealmResults<DeviceConfig> deviceConfigs = realm.where(DeviceConfig.class).findAll();
                for (DeviceConfig config : deviceConfigs) {
                    addressSet.add(config.address);
                }
            }

            emitter.onSuccess(addressSet);
        });
    }

}
