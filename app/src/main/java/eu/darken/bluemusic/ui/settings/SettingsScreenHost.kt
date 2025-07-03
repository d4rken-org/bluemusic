package eu.darken.bluemusic.ui.settings

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.darken.bluemusic.common.dagger.ViewModelFactory
import javax.inject.Inject

class SettingsScreenHost @Inject constructor(
    private val viewModelFactory: ViewModelFactory
) {
    
    @Composable
    fun Content(
        onNavigateBack: () -> Unit,
        onNavigateToAdvanced: () -> Unit,
        onNavigateToAbout: () -> Unit
    ) {
        val context = LocalContext.current
        val activity = context as? Activity
        
        val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
        val state by viewModel.state.collectAsStateWithLifecycle()
        
        SettingsScreen(
            state = state,
            onEvent = { event ->
                when (event) {
                    is SettingsEvent.OnPurchaseUpgrade -> {
                        activity?.let { viewModel.onEvent(SettingsEvent.OnPurchaseUpgrade(it)) }
                    }
                    else -> viewModel.onEvent(event)
                }
            },
            onNavigateBack = onNavigateBack,
            onNavigateToAdvanced = onNavigateToAdvanced,
            onNavigateToAbout = onNavigateToAbout
        )
    }
}