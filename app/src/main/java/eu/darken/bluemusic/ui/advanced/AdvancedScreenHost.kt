package eu.darken.bluemusic.ui.advanced

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.darken.bluemusic.common.dagger.ViewModelFactory
import javax.inject.Inject

class AdvancedScreenHost @Inject constructor(
    private val viewModelFactory: ViewModelFactory
) {
    
    @Composable
    fun Content(
        onNavigateBack: () -> Unit
    ) {
        val viewModel: AdvancedViewModel = viewModel(factory = viewModelFactory)
        val state by viewModel.state.collectAsState()
        
        AdvancedScreen(
            state = state,
            onEvent = viewModel::onEvent,
            onNavigateBack = onNavigateBack
        )
    }
}