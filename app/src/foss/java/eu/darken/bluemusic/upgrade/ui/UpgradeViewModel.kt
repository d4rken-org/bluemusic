package eu.darken.bluemusic.upgrade.ui

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.SingleEventFlow
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.upgrade.core.UpgradeRepoFoss
import javax.inject.Inject

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    navCtrl: NavigationController,
    private val upgradeRepo: UpgradeRepoFoss,
    private val savedState: SavedStateHandle,
) : ViewModel4(dispatcherProvider, logTag("Upgrade", "Screen", "VM"), navCtrl) {

    val events = SingleEventFlow<UpgradeEvent>()

    private var sponsorPageOpenedAt: Long?
        get() = savedState[KEY_OPENED_AT]
        set(value) { savedState[KEY_OPENED_AT] = value }

    private var hasPausedSinceOpen: Boolean
        get() = savedState[KEY_HAS_PAUSED] ?: false
        set(value) { savedState[KEY_HAS_PAUSED] = value }

    fun openSponsor() {
        if (sponsorPageOpenedAt != null) return
        log(tag) { "openSponsor()" }
        sponsorPageOpenedAt = System.currentTimeMillis()
        hasPausedSinceOpen = false
        upgradeRepo.openGithubSponsorsPage()
    }

    fun onPaused() {
        if (sponsorPageOpenedAt != null) {
            hasPausedSinceOpen = true
        }
    }

    fun onResumed() {
        val openedAt = sponsorPageOpenedAt ?: return
        if (!hasPausedSinceOpen) return

        sponsorPageOpenedAt = null
        hasPausedSinceOpen = false

        val elapsed = System.currentTimeMillis() - openedAt
        log(tag) { "onResumed() elapsed=${elapsed}ms" }

        if (elapsed >= MIN_SPONSOR_DURATION_MS) {
            launch {
                upgradeRepo.confirmGithubSponsorsUpgrade()
                navUp()
            }
        } else {
            launch { events.emit(UpgradeEvent.SpendMoreTime) }
        }
    }

    companion object {
        private const val KEY_OPENED_AT = "sponsor_opened_at"
        private const val KEY_HAS_PAUSED = "has_paused_since_open"
        private const val MIN_SPONSOR_DURATION_MS = 10_000L
        private val TAG = logTag("Upgrade", "Screen", "VM")
    }
}
