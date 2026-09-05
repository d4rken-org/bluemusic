package eu.darken.bluemusic.common.ui

import androidx.annotation.CallSuper
import androidx.lifecycle.ViewModel
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag

abstract class ViewModel1(
    open val tag: String = defaultTag()
) : ViewModel() {

    init {
        log(defaultTag()) { "Initialized" }
    }

    @CallSuper
    override fun onCleared() {
        log(tag) { "onCleared()" }
        super.onCleared()
    }

    companion object {
        private fun defaultTag(): String = logTag("ViewModel")
    }
}