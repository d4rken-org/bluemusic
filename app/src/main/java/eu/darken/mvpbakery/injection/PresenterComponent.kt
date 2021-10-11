package eu.darken.mvpbakery.injection

import eu.darken.mvpbakery.base.Presenter

interface PresenterComponent<PresenterT : ComponentPresenter<out Presenter.View, Self>, Self : PresenterComponent<PresenterT, Self>> {
    val presenter: PresenterT
}
