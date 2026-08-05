package eu.darken.bluemusic.common.debug.logging

import android.util.Log
import eu.darken.bluemusic.common.debug.Bugs
import eu.darken.bluemusic.common.error.addSuppressedSafely
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant


class FileLogger(private val logFile: File) : Logging.Logger {
    private var logWriter: OutputStreamWriter? = null

    // Test seam for deterministic fault injection: a failing open is otherwise only reachable through
    // filesystem permissions, which this very start repairs and a root test runner ignores.
    internal var streamFactory: (File) -> OutputStream = { FileOutputStream(it, true) }

    /**
     * Throws if the writer could not be initialized. Swallowing the failure here would install a
     * logger that silently writes nowhere, and the recorder would report a successful start for a
     * recording that can never produce a log file.
     */
    @Suppress("SetWorldWritable", "SetWorldReadable")
    @Synchronized
    fun start() {
        if (logWriter != null) return
        Log.i(TAG, "Starting logger for " + logFile.path)

        val parentDir = logFile.parentFile!!
        if (parentDir.isFile) parentDir.delete()
        parentDir.mkdirs()

        // Only a log file THIS start created may be deleted again when it fails: a session being
        // resumed already holds the recording the user made, and dropping it on a failed restart
        // destroys the very data they were collecting.
        val createdHere = logFile.createNewFile()
        if (createdHere) Log.i(TAG, "File logger writing to ${logFile.path}")
        if (logFile.setReadable(true, false)) Log.i(TAG, "Debug run log read permission set")
        if (logFile.setWritable(true, false)) Log.i(TAG, "Debug run log write permission set")

        val writer = try {
            OutputStreamWriter(streamFactory(logFile))
        } catch (e: IOException) {
            Log.e(TAG, "Log writer failed to open $logFile", e)
            if (createdHere) logFile.delete()
            throw e
        }

        try {
            writer.write("=== BEGIN ${Bugs.processTag} ===\n")
            writer.write("Logfile: $logFile\n")
            writer.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Log writer failed to start", e)
            try {
                writer.close()
            } catch (closeError: IOException) {
                e.addSuppressedSafely(closeError)
            }
            if (createdHere) logFile.delete()
            throw e
        }

        logWriter = writer
        Log.i(TAG, "File logger started.")
    }

    @Synchronized
    fun stop() {
        logWriter?.let {
            logWriter = null
            try {
                it.write("=== END ===\n")
                it.close()
            } catch (ignore: IOException) {
            }
            Log.i(TAG, "File logger stopped.")
        }
    }

    override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
        logWriter?.let {
            try {
                it.write("${Instant.ofEpochMilli(System.currentTimeMillis())}  ${priority.shortLabel}/$tag: $message\n")
                it.flush()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write log line.", e)
                try {
                    it.close()
                } catch (ignore: Exception) {
                }
                logWriter = null
            }
        }
    }

    override fun toString(): String = "FileLogger(file=$logFile)"

    companion object {
        private val TAG = logTag("Debug", "FileLogger")
    }
}

