package eu.darken.mvpbakery.base


import android.os.Bundle
import androidx.lifecycle.LifecycleOwner

interface PresenterRetainer<ViewT : Presenter.View, PresenterT : Presenter<ViewT>> {
    val presenter: PresenterT?
    var stateForwarder: StateForwarder?
    var presenterFactory: PresenterFactory<PresenterT>

    fun attach(lifecycleOwner: LifecycleOwner, callback: Callback<ViewT, PresenterT>)

    interface Callback<ViewT : Presenter.View, PresenterT : Presenter<ViewT>> {
        fun onPresenterAvailable(presenter: PresenterT)
    }

    class DefaultStateListener(private val retainer: PresenterRetainer<*, *>) : StateForwarder.Listener {

        override fun onCreate(savedInstanceState: Bundle?): Boolean {
            if (retainer.presenter is StateListener) {
                (retainer.presenter as StateListener).onRestoreState(savedInstanceState)
                return true
            }
            return false
        }

        override fun onSaveInstanceState(outState: Bundle) {
            if (retainer.presenter is StateListener) {
                (retainer.presenter as StateListener).onSaveState(outState)
            }
        }
    }
}
