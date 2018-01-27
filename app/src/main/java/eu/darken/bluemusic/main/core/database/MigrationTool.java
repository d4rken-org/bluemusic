package eu.darken.bluemusic.main.core.database;

import java.util.Arrays;
import java.util.List;

import io.realm.RealmMigration;
import io.realm.RealmObjectSchema;


public class MigrationTool {
    // Schema 1 -> 2
    private final RealmMigration legacyMigration = (realm, oldVersion, newVersion) -> {
        final RealmObjectSchema con = realm.getSchema().get("DeviceConfig");
        if (con == null) return;

        // Property 'DeviceConfig.musicVolume' has been made optional.
        if (con.hasField("musicVolume")) con.setRequired("musicVolume", false);

        // Property 'DeviceConfig.autoplay' has been added.
        if (!con.hasField("autoplay")) con.addField("autoplay", boolean.class);

        // Property 'DeviceConfig.actionDelay' has been added.
        if (!con.hasField("actionDelay")) con.addField("actionDelay", Long.class);

        // Property 'DeviceConfig.adjustmentDelay' has been added.
        if (!con.hasField("adjustmentDelay")) con.addField("adjustmentDelay", Long.class);

        // Property 'DeviceConfig.callVolume' has been added.
        if (!con.hasField("callVolume")) con.addField("callVolume", Float.class);

        // Property 'DeviceConfig.voiceVolume' has been removed.
        if (con.hasField("voiceVolume")) con.removeField("voiceVolume");
    };

    // Schema 2 -> 3
    private final RealmMigration twoToThreeMigration = (realm, oldVersion, newVersion) -> {
        final RealmObjectSchema con = realm.getSchema().get("DeviceConfig");
        if (con == null) return;

        if (!con.hasField("launchPkg")) con.addField("launchPkg", String.class);
    };

    private final List<RealmMigration> subMigrations = Arrays.asList(legacyMigration, twoToThreeMigration);

    public RealmMigration getMigration() {
        return (realm, oldVersion, newVersion) -> {
            for (RealmMigration m : subMigrations) m.migrate(realm, oldVersion, newVersion);
        };
    }


}
