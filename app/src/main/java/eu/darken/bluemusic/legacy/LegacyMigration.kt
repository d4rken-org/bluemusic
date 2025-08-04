package eu.darken.bluemusic.legacy

import eu.darken.bluemusic.common.datastore.value
import eu.darken.bluemusic.common.datastore.valueBlocking
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.main.core.CurriculumVitae
import eu.darken.bluemusic.main.core.GeneralSettings
import eu.darken.bluemusic.main.core.LegacySettings
import kotlinx.coroutines.runBlocking
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegacyMigration @Inject constructor(
    private val generalSettings: GeneralSettings,
    private val legacySettings: LegacySettings,
    private val curriculumVitae: CurriculumVitae,
    private val realmToRoomMigrator: RealmToRoomMigrator,
) {

    fun migration() = runBlocking {
        log(TAG) { "migration()" }
        if (!generalSettings.legacySettingsMigrationDone.valueBlocking) {
            log(TAG, INFO) { "migration(): Legacy settings migration required" }

            curriculumVitae.setLegacy(
                installedAt = Instant.ofEpochMilli(legacySettings.getInstallTime()),
                launchCount = legacySettings.getLaunchCount(),
            )
            // TODO Migrate LegacySettings

            generalSettings.legacySettingsMigrationDone.value(true)
        }

        if (!generalSettings.legacyDatabaseMigrationDone.valueBlocking) {
            log(TAG, INFO) { "migration():  Realm/Room migration required" }
            try {
                val migrationSuccess = realmToRoomMigrator.migrate()
                if (migrationSuccess) {
                    log(TAG, DEBUG) { "migration(): Data migration completed successfully" }
                    realmToRoomMigrator.cleanupRealmFiles()
                    generalSettings.legacySettingsMigrationDone.value(true)
                }
            } catch (e: Exception) {
                log(TAG, ERROR) { "migration(): Data migration failed: ${e.asLog()}" }
            }
        }
    }

    companion object {
        private val TAG = logTag("Legacy", "Migration")
    }
}