package eu.darken.mvpbakery.base

import android.os.Bundle

class StateForwarder {
    private var internalCallback: Listener? = null
    internal var inState: Bundle? = null
    private var outState: Bundle? = null
    private var isRestoreConsumed = false
    val hasRestoreEvent: Boolean
        get() = !isRestoreConsumed

    fun setListener(internalCallback: Listener?) {
        this.internalCallback = internalCallback
        if (internalCallback == null) return
        if (inState != null) {
            isRestoreConsumed = internalCallback.onCreate(inState)
            if (isRestoreConsumed) inState = null
        }
        outState?.let {
            internalCallback.onSaveInstanceState(it)
            outState = null
        }
    }

    fun onCreate(savedInstanceState: Bundle?) {
        isRestoreConsumed = internalCallback != null && internalCallback!!.onCreate(savedInstanceState)
        if (!isRestoreConsumed) this.inState = savedInstanceState
    }

    fun onSaveInstanceState(outState: Bundle) {
        if (internalCallback != null) {
            internalCallback!!.onSaveInstanceState(outState)
        } else {
            this.outState = outState
        }
    }

    interface Listener {
        /**
         * Call directly after [android.app.Activity.onCreate] or [android.app.Fragment.onCreate]
         *
         * @return true if the instance state was delivered or false if it should be persisted
         */
        fun onCreate(savedInstanceState: Bundle?): Boolean

        /**
         * Call before [android.app.Activity.onSaveInstanceState] or [android.app.Fragment.onSaveInstanceState]
         */
        fun onSaveInstanceState(outState: Bundle)
    }
}
