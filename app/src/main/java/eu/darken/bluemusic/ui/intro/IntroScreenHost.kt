package eu.darken.bluemusic.ui.intro

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.darken.bluemusic.common.dagger.ViewModelFactory
import javax.inject.Inject

class IntroScreenHost @Inject constructor(
    private val viewModelFactory: ViewModelFactory
) {
    
    @Composable
    fun Content(
        onNavigateToMainScreen: () -> Unit
    ) {
        val viewModel: IntroViewModel = viewModel(factory = viewModelFactory)
        val state by viewModel.state.collectAsState()
        
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                viewModel.onEvent(IntroEvent.OnPermissionGranted)
            } else {
                viewModel.onEvent(IntroEvent.OnPermissionDenied)
            }
        }
        
        IntroScreen(
            state = state,
            onEvent = viewModel::onEvent,
            onRequestBluetoothPermission = {
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            },
            onNavigateToMainScreen = onNavigateToMainScreen
        )
    }
}