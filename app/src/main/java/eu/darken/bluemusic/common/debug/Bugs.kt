package eu.darken.bluemusic.common.debug

import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag

object Bugs {
    // BlueMusic ships no automatic bug reporter, so this stays null and reporting is logging-only.
    var reporter: AutomaticBugReporter? = null

    fun report(exception: Exception) {
        log(TAG, VERBOSE) { "Reporting $exception" }

        reporter?.notify(exception) ?: run {
            log(TAG, WARN) { "Bug tracking not initialized yet." }
        }
    }

    fun leaveBreadCrumb(crumb: String) {
        log(TAG, VERBOSE) { "Leaving crumb $crumb" }

        reporter?.leaveBreadCrumb(crumb) ?: run {
            log(TAG, WARN) { "Bug tracking not initialized yet." }
        }
    }

    var isDebug = false
    var isTrace = false

    var processTag: String = "Default"

    private val TAG = logTag("Debug", "Bugs")
}
