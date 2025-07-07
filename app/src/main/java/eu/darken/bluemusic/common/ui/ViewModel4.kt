package eu.darken.bluemusic.common.ui

import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.navigation.NavigationDestination


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