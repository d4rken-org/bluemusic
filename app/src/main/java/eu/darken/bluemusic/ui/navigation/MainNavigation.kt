package eu.darken.bluemusic.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import eu.darken.bluemusic.App

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val appComponent = remember { (context.applicationContext as App).appComponent }
    val settings = remember { appComponent.settings() }

    // Check if we need to show onboarding
    val startDestination = if (settings.isShowOnboarding) {
        Screen.Intro.route
    } else {
        Screen.ManagedDevices.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.ManagedDevices.route) {
            appComponent.managedDevicesScreenHost().Content(
                onNavigateToConfig = { deviceAddress ->
                    navController.navigate("config/$deviceAddress")
                },
                onNavigateToDiscover = {
                    navController.navigate(Screen.Discover.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Intro.route) {
            appComponent.introScreenHost().Content(
                onNavigateToMainScreen = {
                    navController.navigate(Screen.ManagedDevices.route) {
                        popUpTo(Screen.Intro.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Config.route,
            arguments = listOf(navArgument("deviceAddress") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceAddress = backStackEntry.arguments?.getString("deviceAddress") ?: ""
            appComponent.configScreenHost().Content(
                deviceAddress = deviceAddress,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Discover.route) {
            appComponent.discoverScreenHost().Content(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            appComponent.settingsScreenHost().Content(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAdvanced = {
                    navController.navigate(Screen.Advanced.route)
                },
                onNavigateToAbout = {
                    navController.navigate(Screen.About.route)
                }
            )
        }

        composable(Screen.Advanced.route) {
            appComponent.advancedScreenHost().Content(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.About.route) {
            appComponent.aboutScreenHost().Content(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

sealed class Screen(val route: String) {
    object ManagedDevices : Screen("managed_devices")
    object Intro : Screen("intro")
    object Config : Screen("config/{deviceAddress}")
    object Discover : Screen("discover")
    object Settings : Screen("settings")
    object Advanced : Screen("advanced")
    object About : Screen("about")
}