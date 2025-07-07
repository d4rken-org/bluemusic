package eu.darken.bluemusic.common.navigation

import androidx.navigation3.runtime.NavBackStack
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.navigation.NavigationDestination
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationController @Inject constructor() {
    private var _backStack: NavBackStack? = null

    private val backStack: NavBackStack
        get() = _backStack ?: error("NavigationController not initialized")

    fun setup(backStack: NavBackStack) {
        log(TAG) { "setup()" }
        _backStack = backStack
    }

    fun up(): Boolean {
        // Don't remove the last element to prevent empty backstack
        if (backStack.size <= 1) {
            log(TAG) { "up() prevented removing the last element in backstack" }
            return false
        }
        val removed = backStack.removeLastOrNull()
        log(TAG) { "up() to ${backStack.lastOrNull()} (removed $removed)" }
        return removed != null
    }

    fun goTo(
        destination: NavigationDestination,
        popUpTo: NavigationDestination? = null,
        inclusive: Boolean = false
    ) {
        log(TAG) { "goTo($destination, popUpTo=$popUpTo, inclusive=$inclusive)" }

        if (popUpTo != null) {
            while (backStack.isNotEmpty() && backStack.last() != popUpTo) {
                val removed = backStack.removeLastOrNull()
                log(TAG) { "Popping $removed while looking for $popUpTo" }
            }

            if (inclusive && backStack.isNotEmpty() && backStack.last() == popUpTo) {
                val removed = backStack.removeLastOrNull()
                log(TAG) { "Popping $removed (inclusive)" }
            }
        }

        backStack.add(destination)
    }

    fun replace(destination: NavigationDestination) {
        backStack.removeLastOrNull()
        backStack.add(destination)
    }

    companion object {
        private val TAG = logTag("Navigation", "Controller")
    }
}
