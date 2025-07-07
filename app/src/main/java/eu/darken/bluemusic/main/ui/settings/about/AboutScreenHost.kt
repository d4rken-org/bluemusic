package eu.darken.bluemusic.main.ui.settings.about

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.darken.bluemusic.common.dagger.ViewModelFactory
import javax.inject.Inject

class AboutScreenHost @Inject constructor(
    private val viewModelFactory: ViewModelFactory
) {
    
    @Composable
    fun Content(
        onNavigateBack: () -> Unit
    ) {
        val viewModel: AboutViewModel = viewModel(factory = viewModelFactory)
        val state by viewModel.state.collectAsState()
        
        AboutScreen(
            state = state,
            onEvent = viewModel::onEvent,
            onNavigateBack = onNavigateBack
        )
    }
}