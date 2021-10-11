package eu.darken.mvpbakery.base

import android.os.Bundle

interface StateListener {
    fun onRestoreState(inState: Bundle?)
    fun onSaveState(outState: Bundle)
}
