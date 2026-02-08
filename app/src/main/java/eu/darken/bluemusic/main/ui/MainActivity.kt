package eu.darken.bluemusic.main.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.bluemusic.common.BuildConfigWrap
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.error.ErrorEventHandler
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.navigation.NavigationDestination
import eu.darken.bluemusic.common.navigation.NavigationEntry
import eu.darken.bluemusic.common.theming.BlueMusicTheme
import eu.darken.bluemusic.common.theming.ThemeState
import eu.darken.bluemusic.common.ui.Activity2
import eu.darken.bluemusic.main.core.CurriculumVitae
import eu.darken.bluemusic.main.core.GeneralSettings
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : Activity2() {

    private val vm: MainViewModel by viewModels()

    @Inject lateinit var curriculumVitae: CurriculumVitae
    @Inject lateinit var navCtrl: NavigationController
    @Inject lateinit var navigationEntries: Set<@JvmSuppressWildcards NavigationEntry>
    @Inject lateinit var generalSettings: GeneralSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (BuildConfigWrap.DEBUG) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        curriculumVitae.updateAppOpened()

        setContent {
            val themeState by produceState<ThemeState?>(initialValue = null) {
                vm.themeState.collect { value = it }
            }
            val vmState by produceState<MainViewModel.State?>(initialValue = null) {
                vm.state.collect { value = it }
            }
            themeState?.let { themeState ->
                log(TAG) { "Theme state: $themeState" }
                BlueMusicTheme(state = themeState) {
                    ErrorEventHandler(vm)
                    vmState?.let { mainState ->
                        log(TAG) { "Main state: $mainState" }
                        Navigation(mainState)
                    }
                }
            }
        }
    }

    @Composable
    private fun Navigation(state: MainViewModel.State) {
        val start = when (state.startScreen) {
            MainViewModel.State.StartScreen.ONBOARDING -> Nav.Main.Onboarding
            MainViewModel.State.StartScreen.HOME -> Nav.Main.ManageDevices
        }

        val backStack = rememberNavBackStack(start)

        LaunchedEffect(Unit) { navCtrl.setup(backStack) }

        NavDisplay(
            backStack = backStack,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            onBack = {
                // Only handle programmatic navigation
                navCtrl.up()
            },
            entryProvider = entryProvider {
                navigationEntries.forEach { entry ->
                    entry.apply {
                        log(TAG) { "Set up navigation entry: $this" }
                        setup()
                    }
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        vm.checkUpgrades()
    }

    companion object {
        private val TAG = logTag("Main", "Activity")
    }
}