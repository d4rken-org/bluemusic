package eu.darken.bluemusic.main.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.BuildConfigWrap
import eu.darken.bluemusic.common.WebpageTool
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.SingleEventFlow
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    dispatcherProvider: DispatcherProvider,
    navCtrl: NavigationController,
    private val webpageTool: WebpageTool,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel4(dispatcherProvider, logTag("Settings", "ViewModel"), navCtrl) {

    val events = SingleEventFlow<SettingsEvent>()

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

    fun copyVersionToClipboard() = launch {
        log(tag) { "copyVersionToClipboard()" }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("BlueMusic Version", BuildConfigWrap.VERSION_DESCRIPTION)
        clipboard.setPrimaryClip(clip)
        events.emit(SettingsEvent.VersionCopied)
    }

    data class State(
        val versionText: String = BuildConfigWrap.VERSION_DESCRIPTION,
        val isUpgraded: Boolean = false,
    )
}
