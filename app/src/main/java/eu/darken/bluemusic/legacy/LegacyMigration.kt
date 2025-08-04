package eu.darken.bluemusic.legacy

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.datastore.value
import eu.darken.bluemusic.common.datastore.valueBlocking
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.currentDevices
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
    private val devicesSettings: DevicesSettings,
    private val legacySettings: LegacySettings,
    private val curriculumVitae: CurriculumVitae,
    private val realmToRoomMigrator: RealmToRoomMigrator,
    private val deviceRepo: DeviceRepo,
) {

    fun migration() = runBlocking {
        log(TAG) { "migration()" }
        if (!generalSettings.legacyDatabaseMigrationDone.valueBlocking) {
            log(TAG, INFO) { "migration():  Realm/Room migration required" }
            try {
                val migrationSuccess = realmToRoomMigrator.migrate()
                if (migrationSuccess) {
                    log(TAG, DEBUG) { "migration(): Data migration completed successfully" }
                    realmToRoomMigrator.cleanUp()
                    generalSettings.legacyDatabaseMigrationDone.value(true)
                }
            } catch (e: Exception) {
                log(TAG, ERROR) { "migration(): Data migration failed: ${e.asLog()}" }
            }
        } else {
            log(TAG, INFO) { "migration(): Realm/Room migration already done" }
        }

        if (!generalSettings.legacySettingsMigrationDone.valueBlocking) {
            log(TAG, INFO) { "migration(): Legacy settings migration required" }

            curriculumVitae.setLegacy(
                installedAt = Instant.ofEpochMilli(legacySettings.getInstallTime()),
                launchCount = legacySettings.getLaunchCount(),
            )

            devicesSettings.isEnabled.value(legacySettings.isEnabled())
            devicesSettings.restoreOnBoot.value(legacySettings.isBootRestoreEnabled())
            generalSettings.legacySettingsMigrationDone.value(true)

            val devices = deviceRepo.currentDevices()

            val visibleAdjustment = legacySettings.isVolumeAdjustedVisibly()
            log(TAG) { "migration(): Visible adjustment: $visibleAdjustment" }
            val volumeObserving = legacySettings.isVolumeChangeListenerEnabled()
            log(TAG) { "migration(): Volume observing: $volumeObserving" }
            devices.map { it.address }.forEach { addr ->
                log(TAG) { "migration(): Updating $addr" }
                deviceRepo.updateDevice(addr) {
                    it.copy(
                        visibleAdjustments = visibleAdjustment,
                        volumeObserving = volumeObserving,
                    )
                }
            }

            devices.singleOrNull { it.type == SourceDevice.Type.PHONE_SPEAKER }?.let { speakerDev ->
                deviceRepo.updateDevice(speakerDev.address) {
                    val speakerAutoSave = legacySettings.isSpeakerAutoSaveEnabled()
                    log(TAG) { "migration(): Saving  isSpeakerAutoSaveEnabled=$speakerAutoSave" }
                    it.copy(volumeSaveOnDisconnect = speakerAutoSave)
                }
            }
        } else {
            log(TAG, INFO) { "migration(): Legacy settings migration already done" }
        }
    }

    companion object {
        private val TAG = logTag("Legacy", "Migration")
    }
}