package eu.darken.bluemusic.common.debug.recorder.core

import eu.darken.bluemusic.common.debug.logging.FileLogger
import eu.darken.bluemusic.common.debug.logging.Logging
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.error.addSuppressedSafely
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject

class Recorder @Inject constructor() {
    private val mutex = Mutex()
    private var fileLogger: FileLogger? = null

    val isRecording: Boolean
        get() = path != null

    var path: File? = null
        private set

    /**
     * Nothing is published and no logger is installed until the writer is live: a failing
     * [FileLogger.start] would otherwise leave this recorder claiming to record into a file that
     * receives nothing.
     */
    suspend fun start(path: File): Unit = mutex.withLock {
        if (fileLogger != null) return@withLock

        val logger = FileLogger(path)
        logger.start()

        this.path = path
        fileLogger = logger

        Logging.install(logger)
        log(TAG, INFO) { "Now logging to file!" }
    }

    /**
     * Every teardown step runs, no matter what the ones before it did: the logger leaves the
     * registry BEFORE any diagnostic it would still receive itself, the published state is cleared
     * in the outermost finally, and only then is the first failure rethrown with the later ones
     * suppressed onto it. This recorder must never stay globally installed while the module reports
     * it as stopped.
     */
    suspend fun stop(): Unit = mutex.withLock {
        val logger = fileLogger
        var failure: Throwable? = null

        fun step(block: () -> Unit) {
            try {
                block()
            } catch (e: Throwable) {
                val previous = failure
                if (previous == null) failure = e else previous.addSuppressedSafely(e)
            }
        }

        try {
            if (logger != null) {
                step { Logging.remove(logger) }
                step { log(TAG, INFO) { "Stopped file-logger-tree: $logger" } }
                step { logger.stop() }
            }
        } finally {
            fileLogger = null
            this.path = null
        }

        failure?.let { throw it }
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "Recorder")
    }
}
