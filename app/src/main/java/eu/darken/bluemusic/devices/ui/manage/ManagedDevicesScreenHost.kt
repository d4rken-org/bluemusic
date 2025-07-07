package eu.darken.bluemusic.devices.ui.manage

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.darken.bluemusic.common.dagger.ViewModelFactory
import eu.darken.bluemusic.common.ApiHelper
import javax.inject.Inject

class ManagedDevicesScreenHost @Inject constructor(
    private val viewModelFactory: ViewModelFactory
) {
    
    @Composable
    fun Content(
        onNavigateToConfig: (deviceAddress: String) -> Unit,
        onNavigateToDiscover: () -> Unit,
        onNavigateToSettings: () -> Unit
    ) {
        val context = LocalContext.current
        val activity = context as? Activity
        
        val viewModel: ManagedDevicesViewModel = viewModel(factory = viewModelFactory)
        val state by viewModel.state.collectAsState()
        
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                viewModel.onEvent(ManagedDevicesEvent.OnNotificationPermissionsGranted)
            }
        }
        
        ManagedDevicesScreen(
            state = state,
            onEvent = { event ->
                when (event) {
                    is ManagedDevicesEvent.OnDeviceClicked -> {
                        onNavigateToConfig(event.device.address)
                    }
                    is ManagedDevicesEvent.OnAddDeviceClicked -> {
                        onNavigateToDiscover()
                    }
                    else -> viewModel.onEvent(event)
                }
            },
            onNavigateToConfig = onNavigateToConfig,
            onNavigateToDiscover = onNavigateToDiscover,
            onNavigateToSettings = onNavigateToSettings
        )
        
        // Handle notification permission request
        LaunchedEffect(state.showNotificationPermissionHint) {
            if (state.showNotificationPermissionHint && ApiHelper.hasAndroid13()) {
                if (activity != null && 
                    ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}