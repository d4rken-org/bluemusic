package eu.darken.bluemusic.ui.discover

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.darken.bluemusic.common.dagger.ViewModelFactory
import javax.inject.Inject

class DiscoverScreenHost @Inject constructor(
    private val viewModelFactory: ViewModelFactory
) {
    
    @Composable
    fun Content(
        onNavigateBack: () -> Unit
    ) {
        val context = LocalContext.current
        val activity = context as? Activity
        
        val viewModel: DiscoverViewModel = viewModel(factory = viewModelFactory)
        val state by viewModel.state.collectAsState()
        
        DiscoverScreen(
            state = state,
            onEvent = { event ->
                when (event) {
                    is DiscoverEvent.OnPurchaseUpgrade -> {
                        activity?.let { viewModel.onEvent(DiscoverEvent.OnPurchaseUpgrade(it)) }
                    }
                    else -> viewModel.onEvent(event)
                }
            },
            onNavigateBack = onNavigateBack
        )
    }
}