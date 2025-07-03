package eu.darken.bluemusic.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import eu.darken.bluemusic.App

@Composable
fun BlueMusicNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = BlueMusicDestination.ManagedDevices.route,
    onboardingComplete: Boolean = true
) {
    NavHost(
        navController = navController,
        startDestination = if (onboardingComplete) startDestination else BlueMusicDestination.Onboarding.route,
        modifier = modifier
    ) {
        composable(BlueMusicDestination.ManagedDevices.route) {
            val context = LocalContext.current
            val screenHost = remember {
                (context.applicationContext as App).appComponent
                    .managedDevicesScreenHost()
            }
            
            screenHost.Content(
                onNavigateToConfig = { address ->
                    navController.navigate(BlueMusicDestination.DeviceConfig.createRoute(address))
                },
                onNavigateToDiscover = {
                    navController.navigate(BlueMusicDestination.Discover.route)
                },
                onNavigateToSettings = {
                    navController.navigate(BlueMusicDestination.Settings.route)
                }
            )
        }
        
        composable(BlueMusicDestination.Onboarding.route) {
            val context = LocalContext.current
            val screenHost = remember {
                (context.applicationContext as App).appComponent
                    .introScreenHost()
            }
            
            screenHost.Content(
                onNavigateToMainScreen = {
                    navController.navigate(BlueMusicDestination.ManagedDevices.route) {
                        popUpTo(BlueMusicDestination.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(BlueMusicDestination.Discover.route) {
            val context = LocalContext.current
            val screenHost = remember {
                (context.applicationContext as App).appComponent
                    .discoverScreenHost()
            }
            
            screenHost.Content(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = BlueMusicDestination.DeviceConfig.ROUTE,
            arguments = listOf(
                androidx.navigation.navArgument(BlueMusicDestination.DeviceConfig.ADDRESS_ARG) {
                    type = androidx.navigation.NavType.StringType
                }
            )
        ) { backStackEntry ->
            val address = backStackEntry.arguments?.getString(BlueMusicDestination.DeviceConfig.ADDRESS_ARG) ?: ""
            val context = LocalContext.current
            val screenHost = remember {
                (context.applicationContext as App).appComponent
                    .configScreenHost()
            }
            
            screenHost.Content(
                deviceAddress = address,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(BlueMusicDestination.Settings.route) {
            val context = LocalContext.current
            val screenHost = remember {
                (context.applicationContext as App).appComponent
                    .settingsScreenHost()
            }
            
            screenHost.Content(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToAdvanced = {
                    navController.navigate(BlueMusicDestination.AdvancedSettings.route)
                },
                onNavigateToAbout = {
                    navController.navigate(BlueMusicDestination.About.route)
                }
            )
        }
        
        composable(BlueMusicDestination.AdvancedSettings.route) {
            val context = LocalContext.current
            val screenHost = remember {
                (context.applicationContext as App).appComponent
                    .advancedScreenHost()
            }
            
            screenHost.Content(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(BlueMusicDestination.About.route) {
            val context = LocalContext.current
            val screenHost = remember {
                (context.applicationContext as App).appComponent
                    .aboutScreenHost()
            }
            
            screenHost.Content(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}