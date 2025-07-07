package eu.darken.bluemusic.ui.config

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.darken.bluemusic.common.dagger.ViewModelFactory
import eu.darken.bluemusic.util.AppTool
import javax.inject.Inject

class ConfigScreenHost @Inject constructor(
    private val viewModelFactory: ViewModelFactory,
    private val appTool: AppTool
) {
    
    @Composable
    fun Content(
        deviceAddress: String,
        onNavigateBack: () -> Unit
    ) {
        val context = LocalContext.current
        val activity = context as? Activity
        
        val viewModel: ConfigViewModel = viewModel(factory = viewModelFactory)
        val state by viewModel.state.collectAsState()
        
        // Set device address when first composed
        LaunchedEffect(deviceAddress) {
            viewModel.setDeviceAddress(deviceAddress)
        }
        
        // App picker launcher
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            // Handle app picker result if needed
        }
        
        ConfigScreen(
            state = state,
            onEvent = { event ->
                when (event) {
                    is ConfigEvent.OnPurchaseUpgrade -> {
                        activity?.let { viewModel.onEvent(ConfigEvent.OnPurchaseUpgrade(it)) }
                    }
                    else -> viewModel.onEvent(event)
                }
            },
            onNavigateBack = onNavigateBack
        )
        
        // Handle app picker dialog
        LaunchedEffect(state.showAppPickerDialog) {
            if (state.showAppPickerDialog && activity != null) {
                // Get app list and show dialog
                val apps = appTool.apps
                val appList = mutableListOf(AppTool.Item.empty())
                appList.addAll(apps)
                
                // For now, we'll handle this in the viewModel
                // In a real implementation, you might want to use a custom dialog
                viewModel.onEvent(ConfigEvent.OnDismissDialog)
            }
        }
    }
}