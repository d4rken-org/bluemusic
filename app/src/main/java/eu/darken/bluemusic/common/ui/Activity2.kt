package eu.darken.butler.common.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.butler.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.butler.common.debug.logging.log

abstract class Activity2 : ComponentActivity() {
    internal val tag: String =
        logTag("Activity", this.javaClass.simpleName + "(" + Integer.toHexString(hashCode()) + ")")

    override fun onCreate(savedInstanceState: Bundle?) {
        log(tag, VERBOSE) { "onCreate(savedInstanceState=$savedInstanceState)" }
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        log(tag, VERBOSE) { "onResume()" }
        super.onResume()
    }

    override fun onPause() {
        log(tag, VERBOSE) { "onPause()" }
        super.onPause()
    }

    override fun onDestroy() {
        log(tag, VERBOSE) { "onDestroy()" }
        super.onDestroy()
    }
}