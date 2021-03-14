package eu.darken.bluemusic.main.core.database;

import java.util.Arrays;
import java.util.List;

import io.realm.RealmMigration;
import io.realm.RealmObjectSchema;


public class MigrationTool {
    private static final int SCHEMA_VERSION = 9;
    // Schema 1 -> 2
    private final RealmMigration legacyMigration = (realm, oldVersion, newVersion) -> {
        final RealmObjectSchema con = realm.getSchema().get("DeviceConfig");
        if (con == null) return;

        // Property 'DeviceConfig.musicVolume' has been made optional.
        if (con.hasField("musicVolume") && con.isRequired("musicVolume")) {
            con.setRequired("musicVolume", false);
        }

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

    // Schema 3 -> 4
    private final RealmMigration threeToFourMigration = (realm, oldVersion, newVersion) -> {
        final RealmObjectSchema con = realm.getSchema().get("DeviceConfig");
        if (con == null) return;

        // Property 'DeviceConfig.ringVolume' has been added.
        if (!con.hasField("ringVolume")) con.addField("ringVolume", Float.class);
    };

    // Schema 4 -> 5
    private final RealmMigration fourToFiveMigration = (realm, oldVersion, newVersion) -> {
        final RealmObjectSchema con = realm.getSchema().get("DeviceConfig");
        if (con == null) return;

        // Property 'DeviceConfig.notificationVolume' has been added.
        if (!con.hasField("notificationVolume")) con.addField("notificationVolume", Float.class);
    };

    // Schema 5 -> 6
    private final RealmMigration fiveToSixMigration = (realm, oldVersion, newVersion) -> {
        final RealmObjectSchema con = realm.getSchema().get("DeviceConfig");
        if (con == null) return;

        // Property 'DeviceConfig.notificationVolume' has been added.
        if (!con.hasField("monitoringDuration")) con.addField("monitoringDuration", Long.class);
        if (!con.hasField("volumeLock")) con.addField("volumeLock", boolean.class);
    };

    // Schema 6 -> 7
    private final RealmMigration sixToSevenMigration = (realm, oldVersion, newVersion) -> {
        final RealmObjectSchema con = realm.getSchema().get("DeviceConfig");
        if (con == null) return;

        // Property 'DeviceConfig.notificationVolume' has been added.
        if (!con.hasField("keepAwake")) con.addField("keepAwake", boolean.class);
    };

    // Schema 7 -> 8
    private final RealmMigration sevenToEightMigration = (realm, oldVersion, newVersion) -> {
        final RealmObjectSchema con = realm.getSchema().get("DeviceConfig");
        if (con == null) return;

        // Property 'DeviceConfig.notificationVolume' has been added.
        if (!con.hasField("alarmVolume")) con.addField("alarmVolume", Float.class);
    };

    // Schema 8 -> 9
    private final RealmMigration eightToNineMigration = (realm, oldVersion, newVersion) -> {
        final RealmObjectSchema con = realm.getSchema().get("DeviceConfig");
        if (con == null) return;

        // Property 'DeviceConfig.nudgeVolume' has been added.
        if (!con.hasField("nudgeVolume")) con.addField("nudgeVolume", boolean.class);
    };

    private final List<RealmMigration> subMigrations = Arrays.asList(
            legacyMigration,
            twoToThreeMigration,
            threeToFourMigration,
            fourToFiveMigration,
            fiveToSixMigration,
            sixToSevenMigration,
            sevenToEightMigration,
            eightToNineMigration
    );

    public int getSchemaVersion() {
        return SCHEMA_VERSION;
    }

    public RealmMigration getMigration() {
        return (realm, oldVersion, newVersion) -> {
            for (RealmMigration m : subMigrations) m.migrate(realm, oldVersion, newVersion);
        };
    }


}
