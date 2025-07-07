package eu.darken.bluemusic.data.migration

import android.content.Context
import eu.darken.bluemusic.data.device.DeviceConfigDao
import eu.darken.bluemusic.data.device.DeviceConfigEntity
import eu.darken.bluemusic.main.core.database.DeviceConfig
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class RealmToRoomMigrator @Inject constructor(
    private val context: Context,
    private val deviceConfigDao: DeviceConfigDao
) {
    
    suspend fun migrate(): Boolean = withContext(Dispatchers.IO) {
        try {
            Timber.d("Starting Realm to Room migration")
            
            // Check if migration is needed
            val prefs = context.getSharedPreferences("migration_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("realm_to_room_completed", false)) {
                Timber.d("Migration already completed")
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
                
                Timber.d("Found ${realmDevices.size} devices to migrate")
                
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
                        volumeLock = realmDevice.isVolumeLock,
                        keepAwake = realmDevice.isKeepAwake,
                        nudgeVolume = realmDevice.isNudgeVolume,
                        autoplay = realmDevice.isAutoplay,
                        launchPkg = realmDevice.launchPkg
                    )
                    
                    deviceConfigDao.insertDevice(roomDevice)
                    Timber.d("Migrated device: ${roomDevice.address}")
                }
                
                // Mark migration as completed
                prefs.edit().putBoolean("realm_to_room_completed", true).apply()
                
                Timber.d("Migration completed successfully")
                true
            } finally {
                realm.close()
            }
        } catch (e: Exception) {
            Timber.e(e, "Migration failed")
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
                Timber.d("Realm files cleaned up")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup Realm files")
        }
    }
}