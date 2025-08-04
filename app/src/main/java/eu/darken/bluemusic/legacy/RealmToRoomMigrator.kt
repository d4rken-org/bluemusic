package eu.darken.bluemusic.legacy

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.devices.core.database.DeviceDatabase
import eu.darken.bluemusic.devices.core.database.legacy.DeviceConfig
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealmToRoomMigrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceDatabase: DeviceDatabase,
) {
    private val realmExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Realm-Migration-Thread")
    }
    private val realmDispatcher = realmExecutor.asCoroutineDispatcher()

    private fun getRealmFiles(): List<File> {
        val filesDir = context.filesDir
        return listOf(
            "default.realm",
            "default.realm.lock",
            "default.realm.note",
            ".realm.temp",
            "default.realm.management"
        ).map { File(filesDir, it) }
    }

    suspend fun migrate(): Boolean = withContext(NonCancellable + realmDispatcher) {
        try {
            log(TAG) { "Starting Realm to Room migration" }

            val anyRealmFileExists = getRealmFiles().any { it.exists() }

            if (!anyRealmFileExists) {
                log(TAG) { "No Realm files found, skipping migration" }
                return@withContext true
            }

            Realm.init(context)

            val realmConfig = RealmConfiguration.Builder().apply {
                schemaVersion(9)
            }.build()

            val realm = Realm.getInstance(realmConfig)

            val migrationResult = try {
                val realmDevices = realm.where(DeviceConfig::class.java).findAll()

                log(TAG) { "Found ${realmDevices.size} devices to migrate" }

                // Copy data to avoid accessing Realm objects after closing
                realmDevices.map { realmDevice ->
                    DeviceConfigEntity(
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
                        launchPkgs = realmDevice.launchPkg?.let { listOf(it) } ?: emptyList()
                    )
                }
            } finally {
                realm.close()
            }

            migrationResult.forEach { roomDevice ->
                deviceDatabase.devices.updateDevice(roomDevice)
                log(TAG) { "Migrated device: ${roomDevice.address}" }
            }

            log(TAG) { "Migration completed successfully" }
            true
        } catch (e: Exception) {
            log(TAG, ERROR) { "Migration failed: ${e.asLog()}" }
            false
        }
    }

    suspend fun cleanUp() = withContext(realmDispatcher + NonCancellable) {
        try {
            val realmFiles = getRealmFiles()

            realmFiles.forEach { file ->
                if (file.exists()) {
                    val deleted = if (file.isDirectory) {
                        file.deleteRecursively()
                    } else {
                        file.delete()
                    }

                    if (deleted) {
                        log(TAG) { "Deleted: ${file.name}" }
                    } else {
                        log(TAG) { "Failed to delete: ${file.name}" }
                    }
                }
            }

            log(TAG) { "Realm cleanup completed" }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to cleanup Realm files: ${e.asLog()}" }
        }
    }

    companion object {

        private val TAG = logTag("Legacy", "Migration", "RealmToRoom")
    }

}