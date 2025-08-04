package eu.darken.bluemusic.common.architecture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

abstract class BaseViewModel<State : Any, Event : Any>(
    initialState: State
) : ViewModel() {
    
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()
    
    protected val currentState: State
        get() = _state.value
    
    protected fun updateState(reducer: State.() -> State) {
        _state.value = currentState.reducer()
    }
    
    abstract fun onEvent(event: Event)
    
    protected fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
        }
    }
}