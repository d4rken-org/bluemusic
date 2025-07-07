package eu.darken.bluemusic.devices.core.database.legacy

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.debug.logging.Logging
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.database.DeviceConfigDao
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.devices.core.database.DeviceDatabase
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealmToRoomMigrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceDatabase: DeviceDatabase,
) {

    companion object {
        private val TAG = logTag("RealmToRoomMigrator")
    }

    suspend fun migrate(): Boolean = withContext(Dispatchers.IO) {
        try {
            log(TAG) { "Starting Realm to Room migration" }

            // Check if migration is needed
            val prefs = context.getSharedPreferences("migration_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("realm_to_room_completed", false)) {
                log(TAG) { "Migration already completed" }
                return@withContext true
            }

            // Initialize Realm
            Realm.init(context)
            val realmConfig = RealmConfiguration.Builder()
                .schemaVersion(9) // Current Realm schema version
                .build()

            val realm = Realm.getInstance(realmConfig)

            try {
                // Read all device configs from Realm
                val realmDevices = realm.where(DeviceConfig::class.java).findAll()

                log(TAG) { "Found ${realmDevices.size} devices to migrate" }

                // Convert and insert into Room
                realmDevices.forEach { realmDevice ->
                    val roomDevice = DeviceConfigEntity(
                        address = realmDevice.address ?: "",
                        lastConnected = realmDevice.lastConnected,
                        actionDelay = realmDevice.actionDelay,
                        adjustmentDelay = realmDevice.adjustmentDelay,
                        monitoringDuration = realmDevice.monitoringDuration,
                        musicVolume = realmDevice.musicVolume,
                        callVolume = realmDevice.callVolume,
                        ringVolume = realmDevice.ringVolume,
                        notificationVolume = realmDevice.notificationVolume,
                        alarmVolume = realmDevice.alarmVolume,
                        volumeLock = realmDevice.volumeLock,
                        keepAwake = realmDevice.keepAwake,
                        nudgeVolume = realmDevice.nudgeVolume,
                        autoplay = realmDevice.autoplay,
                        launchPkg = realmDevice.launchPkg
                    )

                    deviceDatabase.devices.insertDevice(roomDevice)
                    log(TAG) { "Migrated device: ${roomDevice.address}" }
                }

                // Mark migration as completed
                prefs.edit().putBoolean("realm_to_room_completed", true).apply()

                log(TAG) { "Migration completed successfully" }
                true
            } finally {
                realm.close()
            }
        } catch (e: Exception) {
            log(TAG, Logging.Priority.ERROR) { "Migration failed: ${e.asLog()}" }
            false
        }
    }

    suspend fun cleanupRealmFiles() = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("migration_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("realm_cleanup_completed", false)) {
                // Delete Realm files after successful migration
                context.deleteDatabase("default.realm")
                context.deleteDatabase("default.realm.lock")
                context.deleteDatabase("default.realm.note")
                context.deleteDatabase("default.realm.management")

                prefs.edit().putBoolean("realm_cleanup_completed", true).apply()
                log(TAG) { "Realm files cleaned up" }
            }
        } catch (e: Exception) {
            log(TAG, Logging.Priority.ERROR) { "Failed to cleanup Realm files: ${e.asLog()}" }
        }
    }
}