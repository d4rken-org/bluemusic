package eu.darken.bluemusic.main.backup.core

sealed class BackupError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class UnsupportedFormatVersion(val version: Int) :
        BackupError("Unsupported backup format version: $version")

    class MalformedBackup(cause: Throwable) :
        BackupError("Backup file is malformed: ${cause.message}", cause)

    class MissingBackupJson :
        BackupError("ZIP archive does not contain a valid backup.json entry")

    class BackupIoError(cause: Throwable) :
        BackupError("I/O error during backup operation: ${cause.message}", cause)
}
