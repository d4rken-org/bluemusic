package eu.darken.bluemusic.main.backup.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.BuildConfigWrap
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.database.DeviceDatabase
import eu.darken.bluemusic.main.core.GeneralSettings
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRestoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceDatabase: DeviceDatabase,
    private val devicesSettings: DevicesSettings,
    private val generalSettings: GeneralSettings,
    private val json: Json,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val prettyJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    data class BackupResult(
        val deviceConfigCount: Int,
    )

    data class ParseResult(
        val backup: AppBackup,
        val versionMismatch: Boolean,
        val enumWarnings: List<String>,
    )

    data class RestoreResult(
        val deviceCount: Int,
        val skippedCount: Int,
    )

    suspend fun createBackup(outputUri: Uri): BackupResult = withContext(dispatcherProvider.IO) {
        log(TAG, INFO) { "createBackup(uri=$outputUri)" }

        val backup = gatherBackupData()

        log(
            TAG,
            DEBUG
        ) { "Backup metadata: formatVersion=${backup.formatVersion}, appVersion=${backup.appVersion}, createdAt=${backup.createdAt}" }
        log(
            TAG,
            DEBUG
        ) { "Backup contents: ${backup.deviceConfigs.size} devices, devicesSettings=${backup.devicesSettings}, generalSettings=${backup.generalSettings}" }

        backup.deviceConfigs.forEachIndexed { i, device ->
            log(TAG, VERBOSE) { "Backup device[$i]: ${device.address} (name=${device.customName})" }
        }

        val jsonString = json.encodeToString(AppBackup.serializer(), backup)
        log(TAG, VERBOSE) { "Backup JSON:\n${prettyJson.encodeToString(AppBackup.serializer(), backup)}" }

        try {
            val outputStream = context.contentResolver.openOutputStream(outputUri)
                ?: throw BackupError.BackupIoError(IOException("Could not open output stream for $outputUri"))

            outputStream.use { raw ->
                ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                    zip.putNextEntry(ZipEntry(BACKUP_JSON_ENTRY))
                    zip.write(jsonString.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        } catch (e: BackupError) {
            throw e
        } catch (e: Exception) {
            throw BackupError.BackupIoError(e)
        }

        log(TAG, INFO) { "Backup created: ${backup.deviceConfigs.size} devices, ${jsonString.length} bytes JSON" }
        BackupResult(deviceConfigCount = backup.deviceConfigs.size)
    }

    suspend fun parseBackup(inputUri: Uri): ParseResult = withContext(dispatcherProvider.IO) {
        log(TAG, INFO) { "parseBackup(uri=$inputUri)" }

        val jsonString = try {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: throw BackupError.BackupIoError(IOException("Could not open input stream for $inputUri"))

            inputStream.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zip ->
                    var foundBytes: ByteArray? = null
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == BACKUP_JSON_ENTRY) {
                            foundBytes = zip.readBytes()
                        }
                        entry = zip.nextEntry
                    }
                    foundBytes?.toString(Charsets.UTF_8) ?: throw BackupError.MissingBackupJson()
                }
            }
        } catch (e: BackupError) {
            throw e
        } catch (e: Exception) {
            throw BackupError.BackupIoError(e)
        }

        log(TAG, VERBOSE) { "Loaded JSON (${jsonString.length} bytes):\n${jsonString.toComparableJson()}" }

        val backup = try {
            json.decodeFromString(AppBackup.serializer(), jsonString)
        } catch (e: Exception) {
            throw BackupError.MalformedBackup(e)
        }

        log(TAG, DEBUG) { "Parsed: formatVersion=${backup.formatVersion}, appVersion=${backup.appVersion}, createdAt=${backup.createdAt}" }
        log(
            TAG,
            DEBUG
        ) { "Parsed: ${backup.deviceConfigs.size} devices, devicesSettings=${backup.devicesSettings}, generalSettings=${backup.generalSettings}" }

        if (backup.formatVersion != CURRENT_FORMAT_VERSION) {
            log(TAG, WARN) { "Unsupported format version: ${backup.formatVersion} (expected $CURRENT_FORMAT_VERSION)" }
            throw BackupError.UnsupportedFormatVersion(backup.formatVersion)
        }

        val versionMismatch = backup.appVersionCode != BuildConfigWrap.VERSION_CODE
        val enumWarnings = collectEnumWarnings(backup)

        if (versionMismatch) {
            log(TAG, WARN) { "Version mismatch: backup=${backup.appVersion}, current=${BuildConfigWrap.VERSION_NAME}" }
        }
        enumWarnings.forEach { log(TAG, WARN) { "Enum warning: $it" } }

        backup.deviceConfigs.forEachIndexed { i, device ->
            log(TAG, VERBOSE) { "Parsed device[$i]: ${device.address} (name=${device.customName})" }
        }

        log(
            TAG,
            INFO
        ) { "Parsed backup: ${backup.deviceConfigs.size} devices, versionMismatch=$versionMismatch, ${enumWarnings.size} enum warnings" }
        ParseResult(backup = backup, versionMismatch = versionMismatch, enumWarnings = enumWarnings)
    }

    suspend fun applyRestore(backup: AppBackup, skipExisting: Boolean): RestoreResult =
        withContext(dispatcherProvider.IO) {
            val deduped = backup.deviceConfigs.distinctBy { it.address }
            val duplicateCount = backup.deviceConfigs.size - deduped.size
            log(TAG, INFO) {
                "applyRestore(devices=${deduped.size}, skipExisting=$skipExisting, duplicatesDropped=$duplicateCount)"
            }
            log(TAG, VERBOSE) { "Restore JSON:\n${prettyJson.encodeToString(AppBackup.serializer(), backup)}" }

            val dao = deviceDatabase.devices
            var restoredCount = 0
            var skippedCount = 0

            try {
                deviceDatabase.withTransaction {
                    val existing = dao.getAllDevicesOnce().associateBy { it.address }.keys
                    log(TAG, DEBUG) { "Existing devices (${existing.size}): $existing" }

                    deduped.forEach { deviceBackup ->
                        if (skipExisting && deviceBackup.address in existing) {
                            log(TAG, DEBUG) { "Skipping existing device: ${deviceBackup.address} (name=${deviceBackup.customName})" }
                            skippedCount++
                            return@forEach
                        }
                        val entity = deviceBackup.toEntity()
                        val isUpdate = deviceBackup.address in existing
                        log(TAG, DEBUG) { "${if (isUpdate) "Updating" else "Inserting"} device: ${entity.toCompactString()}" }
                        dao.updateDevice(entity)
                        restoredCount++
                    }
                }
            } catch (e: Exception) {
                log(TAG, WARN) { "Device-write transaction failed, rolled back: ${e.asLog()}" }
                throw e
            }

            log(TAG, DEBUG) { "Restoring DevicesSettings: ${backup.devicesSettings}" }
            devicesSettings.applyBackup(backup.devicesSettings)

            log(TAG, DEBUG) { "Restoring GeneralSettings: ${backup.generalSettings}" }
            generalSettings.applyBackup(backup.generalSettings)

            log(TAG, INFO) { "Restore complete: $restoredCount restored, $skippedCount skipped" }
            RestoreResult(deviceCount = restoredCount, skippedCount = skippedCount)
        }

    fun resolveFileName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment ?: uri.toString()
    }

    private suspend fun gatherBackupData(): AppBackup {
        val devices = deviceDatabase.devices.getAllDevicesOnce()
        return AppBackup(
            formatVersion = CURRENT_FORMAT_VERSION,
            appVersion = BuildConfigWrap.VERSION_NAME,
            appVersionCode = BuildConfigWrap.VERSION_CODE,
            createdAt = Instant.now().toString(),
            deviceConfigs = devices.map { it.toBackup() },
            devicesSettings = devicesSettings.toBackup(),
            generalSettings = generalSettings.toBackup(),
        )
    }

    private fun collectEnumWarnings(backup: AppBackup): List<String> = buildList {
        backup.deviceConfigs.forEach { device ->
            addAll(device.detectUnknownEnums())
        }
        addAll(generalSettings.detectUnknownEnums(backup.generalSettings))

        val duplicates = backup.deviceConfigs
            .groupingBy { it.address }
            .eachCount()
            .filter { it.value > 1 }
            .keys
        duplicates.forEach { address ->
            add("Backup contains duplicate entries for device $address, only the first will be applied")
        }
    }

    private fun String.toComparableJson(): String = try {
        val element = Json.parseToJsonElement(this)
        prettyJson.encodeToString(JsonElement.serializer(), element)
    } catch (e: Exception) {
        this
    }

    companion object {
        private val TAG = logTag("Backup", "Restore", "Manager")
        const val CURRENT_FORMAT_VERSION = 1
        const val BACKUP_JSON_ENTRY = "backup.json"
    }
}
