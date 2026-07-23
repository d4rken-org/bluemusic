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
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    navCtrl: NavigationController,
    private val upgradeRepo: UpgradeRepoFoss,
    private val savedState: SavedStateHandle,
) : ViewModel4(dispatcherProvider, logTag("Upgrade", "Screen", "VM"), navCtrl) {

    val events = SingleEventFlow<UpgradeEvent>()

    // Drives the supporter-status view (shown whenever already a supporter) vs the sponsor pitch.
    val state = upgradeRepo.upgradeInfo
        .map { info -> State(isSupporter = info.isUpgraded, supporterSince = info.upgradedAt) }
        .asStateFlow()

    data class State(
        val isSupporter: Boolean = false,
        val supporterSince: Instant? = null,
    )

    private var sponsorPageOpenedAt: Long?
        get() = savedState[KEY_OPENED_AT]
        set(value) { savedState[KEY_OPENED_AT] = value }

    private var hasPausedSinceOpen: Boolean
        get() = savedState[KEY_HAS_PAUSED] ?: false
        set(value) { savedState[KEY_HAS_PAUSED] = value }

    fun openSponsor() {
        if (sponsorPageOpenedAt != null) return
        log(tag) { "openSponsor()" }
        // Only arm the return-after-5s unlock heuristic if the sponsor page actually opened; otherwise
        // an unrelated later pause/resume could grant supporter status with no page ever shown.
        if (!upgradeRepo.openGithubSponsorsPage()) {
            log(tag) { "Sponsor page didn't open; not arming the unlock heuristic" }
            return
        }
        sponsorPageOpenedAt = System.currentTimeMillis()
        hasPausedSinceOpen = false
    }

    // Status-view variant: an existing supporter re-visiting the sponsor page must NOT re-arm the
    // unlock heuristic (which would trigger the "back already?" nudge on return).
    fun openSponsorPage() {
        log(tag) { "openSponsorPage()" }
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
        private const val MIN_SPONSOR_DURATION_MS = 5_000L
        private val TAG = logTag("Upgrade", "Screen", "VM")
    }
}
