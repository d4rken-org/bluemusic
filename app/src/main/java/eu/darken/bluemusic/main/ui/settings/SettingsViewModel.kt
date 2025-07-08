package eu.darken.bluemusic.main.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.BuildConfigWrap
import eu.darken.bluemusic.common.WebpageTool
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    navCtrl: NavigationController,
    private val webpageTool: WebpageTool,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel4(dispatcherProvider, logTag("Settings", "ViewModel"), navCtrl) {

    val state = upgradeRepo.upgradeInfo
        .map { upgradeInfo ->
            State(
                versionText = BuildConfigWrap.VERSION_DESCRIPTION,
                isUpgraded = upgradeInfo.isUpgraded
            )
        }
        .asStateFlow()

    fun openUrl(url: String) {
        log(tag) { "openUrl($url)" }
        webpageTool.open(url)
    }

    data class State(
        val versionText: String = BuildConfigWrap.VERSION_DESCRIPTION,
        val isUpgraded: Boolean = false,
    )
}
