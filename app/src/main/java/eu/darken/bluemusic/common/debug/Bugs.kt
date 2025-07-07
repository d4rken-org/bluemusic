package eu.darken.bluemusic.common.debug

import eu.darken.bluemusic.common.debug.logging.logTag

object Bugs {
    var isDebug = false
    var isTrace = false

    var processTag: String = "Default"

    private val TAG = logTag("Debug", "Bugs")
}