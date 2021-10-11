package eu.darken.mvpbakery.injection

import dagger.android.AndroidInjector
import eu.darken.mvpbakery.base.Presenter
import eu.darken.mvpbakery.base.PresenterRetainer

class PresenterInjectionCallback<TargetT : ViewT, ViewT : Presenter.View, PresenterT : ComponentPresenter<ViewT, ComponentT>, ComponentT>(private val injectionTarget: TargetT)
    : PresenterRetainer.Callback<ViewT, PresenterT> where ComponentT : AndroidInjector<TargetT>, ComponentT : PresenterComponent<PresenterT, ComponentT> {

    override fun onPresenterAvailable(presenter: PresenterT) {
        val component = presenter.component
        component.inject(injectionTarget)
    }
}
