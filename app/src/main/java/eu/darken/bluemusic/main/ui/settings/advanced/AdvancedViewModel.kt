package eu.darken.bluemusic.main.ui.settings.advanced

import eu.darken.bluemusic.common.architecture.BaseViewModel
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.main.core.Settings
import javax.inject.Inject

data class AdvancedState(
    val excludeHealthDevices: Boolean = true
)

sealed interface AdvancedEvent {
    data class OnExcludeHealthDevicesToggled(val enabled: Boolean) : AdvancedEvent
}

class AdvancedViewModel @Inject constructor(
    private val settings: Settings,
    private val dispatcherProvider: DispatcherProvider
) : BaseViewModel<AdvancedState, AdvancedEvent>(AdvancedState()) {
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        updateState {
            copy(
                excludeHealthDevices = settings.isHealthDeviceExcluded
            )
        }
    }
    
    override fun onEvent(event: AdvancedEvent) {
        when (event) {
            is AdvancedEvent.OnExcludeHealthDevicesToggled -> toggleExcludeHealthDevices(event.enabled)
        }
    }
    
    private fun toggleExcludeHealthDevices(enabled: Boolean) {
        // TODO: Add setter for isHealthDeviceExcluded
        updateState { copy(excludeHealthDevices = enabled) }
    }
}