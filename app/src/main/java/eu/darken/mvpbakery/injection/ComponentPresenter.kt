package eu.darken.mvpbakery.injection


import androidx.annotation.CallSuper
import eu.darken.mvpbakery.base.Presenter

abstract class ComponentPresenter<ViewT : Presenter.View, ComponentT : PresenterComponent<*, *>> : Presenter<ViewT> {
    lateinit var component: ComponentT

    var view: ViewT? = null
        private set

    @CallSuper
    override fun onBindChange(view: ViewT?) {
        this.view = view
    }

    @CallSuper
    override fun onDestroy() {

    }

    @FunctionalInterface
    interface ViewAction<T : Presenter.View> {
        fun runOnView(v: T)
    }

    fun onView(action: ViewAction<ViewT>) {
        view?.let { action.runOnView(it) }
    }

    fun withView(action: (v: ViewT) -> Unit) {
        view?.let { action.invoke(it) }
    }
}
