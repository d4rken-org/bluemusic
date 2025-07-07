package eu.darken.butler.common.ui

import eu.darken.butler.common.coroutine.DispatcherProvider
import eu.darken.butler.common.debug.logging.log
import eu.darken.butler.common.navigation.NavigationController
import eu.darken.butler.common.navigation.NavigationDestination


abstract class ViewModel4(
    dispatcherProvider: DispatcherProvider,
    override val tag: String = defaultTag(),
    private val navCtrl: NavigationController,
) : ViewModel3(dispatcherProvider, tag) {

    fun navTo(
        destination: NavigationDestination,
        popUpTo: NavigationDestination? = null,
        inclusive: Boolean = false
    ) {
        log(tag) { "goTo($destination)" }
        navCtrl.goTo(destination, popUpTo, inclusive)
    }

    fun navUp() {
        log(tag) { "navUp()" }
        navCtrl.up()
    }

    companion object {
        private fun defaultTag(): String = this::class.simpleName ?: "VM3"
    }
}