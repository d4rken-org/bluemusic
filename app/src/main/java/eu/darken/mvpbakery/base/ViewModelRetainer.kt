package eu.darken.mvpbakery.base

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import java.util.*

class ViewModelRetainer<ViewT : Presenter.View, PresenterT : Presenter<ViewT>> : PresenterRetainer<ViewT, PresenterT> {
    companion object {
        private fun getKey(owner: LifecycleOwner): String {
            return (owner.javaClass.canonicalName ?: owner.javaClass.name) + ".MVPBakery.Container." + "Default"
        }
    }

    override val presenter: PresenterT?
        get() = container?.presenter
    internal val repo: ContainerRepo
    internal var container: Container<ViewT, PresenterT>? = null
    override var stateForwarder: StateForwarder? = null
        set(value) {
            field = value
            stateForwarder?.setListener(PresenterRetainer.DefaultStateListener(this))
        }
    override lateinit var presenterFactory: PresenterFactory<PresenterT>

    constructor(appCompatActivity: AppCompatActivity) {
        repo = ViewModelProviders.of(appCompatActivity, ContainerRepo.FACTORY).get(ContainerRepo::class.java)
    }

    constructor(supportFragment: Fragment) {
        repo = ViewModelProviders.of(supportFragment, ContainerRepo.FACTORY).get(ContainerRepo::class.java)
    }

    internal class ContainerRepo : ViewModel() {
        private val containerMap = HashMap<Any, Container<*, *>>()

        operator fun <T> get(key: Any): T {
            @Suppress("UNCHECKED_CAST")
            return containerMap[key] as T
        }

        fun put(key: Any, item: Container<*, *>?) {
            containerMap[key] = item!!
        }

        override fun onCleared() {
            for ((_, value) in containerMap) {
                value.destroy()
            }
            containerMap.clear()
            super.onCleared()
        }

        companion object {
            val FACTORY: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    try {
                        return modelClass.newInstance()
                    } catch (e: Exception) {
                        throw RuntimeException(e)
                    }
                }
            }
        }
    }

    override fun attach(lifecycleOwner: LifecycleOwner, callback: PresenterRetainer.Callback<ViewT, PresenterT>) {
        val key = getKey(lifecycleOwner)
        container = repo.get<Container<ViewT, PresenterT>>(key)

        if (container == null || container!!.observer == null) {
            val observer = object : DefaultLifecycleObserver {
                var delayedInit = false

                fun tryInitialization(isDelayedInit: Boolean) {
                    if (container == null) {
                        val result = presenterFactory.createPresenter()
                        if (result.retry) {
                            if (isDelayedInit) {
                                throw IllegalStateException("No presenter after final init attempt.", result.retryException)
                            } else {
                                delayedInit = true
                                return
                            }
                        }

                        container = Container(result.presenter)
                        repo.put(key, container)
                    }

                    container!!.observer = this

                    stateForwarder?.let {
                        if (it.hasRestoreEvent && presenter is StateListener) {
                            (presenter as StateListener).onRestoreState(it.inState)
                        }
                    }

                    callback.onPresenterAvailable(presenter!!)
                }

                override fun onCreate(owner: LifecycleOwner) = tryInitialization(false)

                override fun onStart(owner: LifecycleOwner) {
                    if (delayedInit) {
                        delayedInit = false
                        tryInitialization(true)
                    }
                    @Suppress("UNCHECKED_CAST")
                    presenter?.onBindChange(owner as ViewT)
                }

                override fun onResume(owner: LifecycleOwner) = Unit

                override fun onPause(owner: LifecycleOwner) = Unit

                override fun onStop(owner: LifecycleOwner) = presenter!!.onBindChange(null)

                override fun onDestroy(owner: LifecycleOwner) {
                    stateForwarder?.setListener(null)
                    container?.observer = null
                    owner.lifecycle.removeObserver(this)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
        }
    }

    internal class Container<ViewT : Presenter.View, PresenterT : Presenter<ViewT>>(val presenter: PresenterT?) {
        var observer: LifecycleObserver? = null

        fun destroy() = presenter?.onDestroy()
    }
}
