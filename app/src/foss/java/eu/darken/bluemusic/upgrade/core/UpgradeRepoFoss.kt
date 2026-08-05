package eu.darken.bluemusic.upgrade.core

import eu.darken.bluemusic.common.WebpageTool
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.setupCommonEventHandlers
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn

import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpgradeRepoFoss @Inject constructor(
    @param:AppScope private val scope: CoroutineScope,
    private val fossCache: FossCache,
    private val webpageTool: WebpageTool,
) : UpgradeRepo {

    override val storeSite: String = STORE_SITE
    override val upgradeSite: String = UPGRADE_SITE
    override val betaSite: String = BETA_SITE

    private val refreshTrigger = MutableStateFlow(UUID.randomUUID())

    // Written only from the sharing coroutine (single collector) — no synchronization needed.
    // Recorded INSIDE the flatMapLatest block, upstream of its channel buffer: a downstream onEach
    // can still be waiting on a buffered emission when the inner flow throws, and the retry below
    // would then read a stale (null) value and revoke an entitlement we already saw.
    private var lastKnownInfo: Info? = null

    // Integer, capped backoff: the old 2.0.pow(attempt) formula slept for hours and could overflow
    // Long into a delay(negative) hot loop. Overridable so tests can drive the retry loop without
    // sleeping through the real schedule.
    internal var retryDelayMs: (attempt: Long) -> Long = { (30_000L * (it + 1)).coerceAtMost(300_000L) }

    // Synthesis of the two shapes: the automatic retry loop stays (nothing calls refresh() while an
    // error screen is open, so dropping it would strip the only recovery an idle user gets), but it
    // moves INSIDE the flatMapLatest and gains last-known preservation. Consequences: a late read
    // failure rides on the previously seen Info instead of revoking a supporter's entitlement, and
    // refresh() now CANCELS an in-flight backoff delay and resubscribes immediately — where before
    // a successful persist could take up to five minutes to reach collectors.
    override val upgradeInfo: Flow<UpgradeRepo.Info> = refreshTrigger
        .flatMapLatest {
            // Per-inner-subscription failure-episode counter. A refresh resubscription builds a new
            // inner flow and therefore starts a fresh counter.
            var episodeAttempts = 0L
            fossCache.upgrade.flow
                .map { data ->
                    if (data == null) {
                        Info()
                    } else {
                        Info(
                            isPro = true,
                            upgradedAt = data.upgradedAt,
                            fossUpgradeType = data.upgradeType,
                        )
                    }
                }
                // Same coroutine as the throw below, so the ordering is guaranteed. Only
                // successfully mapped elements pass here — retry emissions go straight downstream
                // and never record themselves as a last known state. A successful read also ends the
                // current failure episode, so the next failure reports and backs off from scratch.
                .onEach {
                    lastKnownInfo = it
                    episodeAttempts = 0L
                }
                .retryWhen { cause, _ ->
                    if (cause is CancellationException) throw cause
                    log(TAG, WARN) { "upgradeInfo read failed (attempt=$episodeAttempts): ${cause.asLog()}" }
                    // Once per failure episode, not once per attempt: the FOSS ViewModel raises an
                    // error dialog for every non-Pro error emission, and a per-attempt emission
                    // would re-raise that dialog on every backoff wake-up. An episode is a run of
                    // CONSECUTIVE failures ending on a successful read (a refresh resubscription
                    // starts a fresh inner flow and counter), so a later, separate episode reports
                    // itself again instead of failing silently. The library's own attempt parameter
                    // is deliberately unused: it counts every retry of the whole inner collection
                    // and never resets on a successful emission, which would both mute every episode
                    // after the first and resume the backoff at an already escalated index.
                    if (episodeAttempts == 0L) emit((lastKnownInfo ?: Info()).copy(error = cause))
                    delay(retryDelayMs(episodeAttempts))
                    episodeAttempts++
                    true
                }
        }
        // MainActivity refreshes on every resume: dedupe the identical re-emissions that produces.
        .distinctUntilChanged()
        .setupCommonEventHandlers(TAG) { "upgradeInfo" }
        .shareIn(scope, SharingStarted.WhileSubscribed(3000L, 0L), replay = 1)

    // Synchronous so the caller learns whether the page actually opened: the FOSS unlock heuristic
    // only arms on a successful launch, and a fire-and-forget coroutine can't report that back.
    fun openGithubSponsorsPage(): Boolean {
        log(TAG) { "openGithubSponsorsPage()" }
        return webpageTool.open(upgradeSite)
    }

    /**
     * Create-only-if-absent inside the store transaction: an existing record (and its upgradedAt —
     * the user-visible "supporter since" date) is never replaced. The VM-level isPro guard alone is
     * not race-free: it reads a shareIn replay that can be stale. Note the kept record is still
     * re-encoded through the current schema — decoded fields are preserved exactly.
     *
     * Caveat, and it is build-type conditional here: [FossCache] enables `onErrorFallbackToDefault`
     * only on RELEASE builds. On release, a stored record that fails to decode reads as null and
     * therefore counts as ABSENT to this transaction, i.e. it gets replaced. That matches the
     * pre-existing read behaviour — such a record already presents the user as free — and
     * re-creating it on the next successful sponsor visit is the recovery path. On debug/beta the
     * decode THROWS instead, so this persist fails outright and the caller restores its
     * pending-return marker for a later retry.
     *
     * @return true if a new record was created, false if an existing record was kept.
     */
    suspend fun persistUpgrade(): Boolean {
        log(TAG) { "persistUpgrade()" }
        val updated = fossCache.upgrade.update { existing ->
            existing ?: FossUpgrade(
                upgradedAt = Instant.now(),
                upgradeType = FossUpgrade.Type.GITHUB_SPONSORS,
            )
        }
        // A returned transaction proves the store is readable again: revive a possibly error-stuck
        // inner flow so the record propagates to collectors still holding the error replay.
        refresh()
        return if (updated.old == null) {
            true
        } else {
            log(TAG, WARN) { "persistUpgrade(): Record already exists (upgradedAt=${updated.old.upgradedAt}), keeping it" }
            false
        }
    }

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        refreshTrigger.value = UUID.randomUUID()
    }

    data class Info(
        override val isPro: Boolean = false,
        override val upgradedAt: Instant? = null,
        val fossUpgradeType: FossUpgrade.Type? = null,
        override val error: Throwable? = null,
    ) : UpgradeRepo.Info {
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS

        // FOSS reads a local cache, so every emission already reflects a real entitlement lookup.
        override val isSettled: Boolean = true
    }

    companion object {
        private const val STORE_SITE = "https://github.com/d4rken-org/bluemusic"
        private const val UPGRADE_SITE = "https://github.com/sponsors/d4rken"
        private const val BETA_SITE = "https://github.com/d4rken-org/bluemusic/releases"
        private val TAG = logTag("Upgrade", "Foss", "Repo")
    }
}