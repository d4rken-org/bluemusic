package eu.darken.mvpbakery.base

import android.app.Activity
import android.app.Fragment
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import eu.darken.mvpbakery.injection.InjectedPresenter
import java.util.*

class MVPBakery<ViewT : Presenter.View, PresenterT : Presenter<ViewT>> internal constructor(builder: Builder<ViewT, PresenterT>) {
    companion object {
        @JvmStatic
        fun <ViewT : Presenter.View, PresenterT : Presenter<ViewT>> builder(): Builder<ViewT, PresenterT> {
            return Builder()
        }
    }

    private val presenterRetainer: PresenterRetainer<ViewT, PresenterT>
    private val stateForwarder: StateForwarder?
    private val presenterFactory: PresenterFactory<PresenterT>
    private val presenterCallbacks: List<PresenterRetainer.Callback<ViewT, PresenterT>>

    val presenter: PresenterT?
        get() = presenterRetainer.presenter

    init {
        this.presenterRetainer = builder.presenterRetainer
        this.stateForwarder = builder.stateForwarder
        this.presenterCallbacks = builder.presenterCallbacks
        this.presenterFactory = builder.presenterFactory
    }

    fun attach(lifecycleOwner: LifecycleOwner) {
        if (stateForwarder != null) this.presenterRetainer.stateForwarder = stateForwarder
        this.presenterRetainer.presenterFactory = presenterFactory
        this.presenterRetainer.attach(lifecycleOwner, object : PresenterRetainer.Callback<ViewT, PresenterT> {
            override fun onPresenterAvailable(presenter: PresenterT) {
                presenterCallbacks.forEach { it.onPresenterAvailable(presenter) }
            }
        })
    }

    class Builder<ViewT, PresenterT : Presenter<ViewT>> internal constructor() where ViewT : Presenter.View, ViewT : LifecycleOwner {
        internal lateinit var presenterFactory: PresenterFactory<PresenterT>
        internal lateinit var presenterRetainer: PresenterRetainer<ViewT, PresenterT>
        internal var stateForwarder: StateForwarder? = null
        internal val presenterCallbacks: MutableList<PresenterRetainer.Callback<ViewT, PresenterT>> = ArrayList()

        /**
         * If you want the presenter to be able to store data via [Activity.onSaveInstanceState] then you need to call this.
         *
         * @param stateForwarder pass a [object][StateForwarder] that you have to call on in onCreate()/onSaveInstance()
         */
        fun stateForwarder(stateForwarder: StateForwarder): Builder<ViewT, PresenterT> {
            this.stateForwarder = stateForwarder
            return this
        }

        fun addPresenterCallback(callback: PresenterRetainer.Callback<ViewT, PresenterT>): Builder<ViewT, PresenterT> {
            presenterCallbacks.add(callback)
            return this
        }

        /**
         * For injection you probably want to pass a [eu.darken.mvpbakery.injection.PresenterInjectionCallback]
         */
        fun presenterRetainer(presenterRetainer: PresenterRetainer<ViewT, PresenterT>): Builder<ViewT, PresenterT> {
            this.presenterRetainer = presenterRetainer
            return this
        }

        /**
         * For injection pass an [InjectedPresenter]
         */
        fun presenterFactory(presenterFactory: PresenterFactory<PresenterT>): Builder<ViewT, PresenterT> {
            this.presenterFactory = presenterFactory
            return this
        }

        fun build(): MVPBakery<ViewT, PresenterT> {
            return MVPBakery(this)
        }

        /**
         * @param lifecycleOwner Your [AppCompatActivity], [Fragment] or [Fragment]
         */
        fun attach(lifecycleOwner: ViewT): MVPBakery<ViewT, PresenterT> {
            val lib = build()
            lib.attach(lifecycleOwner)
            return lib
        }
    }
}