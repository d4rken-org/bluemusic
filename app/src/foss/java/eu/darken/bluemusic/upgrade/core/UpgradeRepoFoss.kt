package eu.darken.bluemusic.upgrade.core

import eu.darken.bluemusic.common.WebpageTool
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.datastore.valueBlocking
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.setupCommonEventHandlers
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

@Singleton
class UpgradeRepoFoss @Inject constructor(
    @param:AppScope private val scope: CoroutineScope,
    private val fossCache: FossCache,
    private val webpageTool: WebpageTool,
) : UpgradeRepo {

    override val mainWebsite: String = SITE

    private val refreshTrigger = MutableStateFlow(UUID.randomUUID())

    override val upgradeInfo: Flow<UpgradeRepo.Info> = combine(
        fossCache.upgrade.flow,
        refreshTrigger
    ) { data, _ ->
        if (data == null) {
            Info()
        } else {
            Info(
                isUpgraded = true,
                upgradedAt = data.upgradedAt,
                fossUpgradeType = data.upgradeType,
            )
        }
    }
        .setupCommonEventHandlers(TAG) { "upgradeInfo" }
        .distinctUntilChanged()
        .retryWhen { error, attempt ->
            emit(Info(error = error))
            delay(30_000L * 2.0.pow(attempt.toDouble()).toLong())
            true
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(3000L, 0L), replay = 1)

    fun launchGithubSponsorsUpgrade() = scope.launch {
        log(TAG) { "launchGithubSponsorsUpgrade()" }
        fossCache.upgrade.valueBlocking = FossUpgrade(
            upgradedAt = Instant.now(),
            upgradeType = FossUpgrade.Type.GITHUB_SPONSORS
        )
        webpageTool.open(mainWebsite)
    }

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        refreshTrigger.value = UUID.randomUUID()
    }

    data class Info(
        override val isUpgraded: Boolean = false,
        override val upgradedAt: Instant? = null,
        val fossUpgradeType: FossUpgrade.Type? = null,
        override val error: Throwable? = null,
    ) : UpgradeRepo.Info {
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS
    }

    companion object {
        private const val SITE = "https://github.com/sponsors/d4rken"
        private val TAG = logTag("Upgrade", "Foss", "Repo")
    }
}