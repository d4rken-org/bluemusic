package eu.darken.bluemusic.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import eu.darken.bluemusic.App
import eu.darken.bluemusic.common.ui.theme.BlueMusicTheme
import eu.darken.bluemusic.data.device.DeviceRepository
import eu.darken.bluemusic.navigation.BlueMusicNavHost
import eu.darken.bluemusic.settings.core.Settings
import eu.darken.bluemusic.settings.core.isOnboardingCompleted
import kotlinx.coroutines.launch

class ComposeMainActivity : ComponentActivity() {
    
    private val settings: Settings by lazy {
        (application as App).appComponent.settings()
    }
    
    private val deviceRepository: DeviceRepository by lazy {
        (application as App).appComponent.deviceRepository()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            BlueMusicTheme {
                BlueMusicApp(
                    settings = settings,
                    deviceRepository = deviceRepository
                )
            }
        }
    }
}

@Composable
private fun BlueMusicApp(
    settings: Settings,
    deviceRepository: DeviceRepository
) {
    val navController = rememberNavController()
    var onboardingComplete by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        launch {
            // Ensure data migration happens on first launch
            deviceRepository.ensureMigration()
        }
        
        // Check if onboarding is complete
        onboardingComplete = settings.isOnboardingCompleted()
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        BlueMusicNavHost(
            navController = navController,
            onboardingComplete = onboardingComplete
        )
    }
}