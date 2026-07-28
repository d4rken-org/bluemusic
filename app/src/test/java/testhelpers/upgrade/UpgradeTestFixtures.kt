package testhelpers.upgrade

import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/**
 * Default fixture for anything reading [UpgradeRepo.upgradeInfo].
 *
 * `isSettled` defaults to true and `error` is a real (null) field: an unsettled or error-carrying
 * fixture makes the Pro gates fail open, which silently turns a "denied" assertion green.
 */
data class FakeUpgradeInfo(
    override val isPro: Boolean = false,
    override val isSettled: Boolean = true,
    override val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS,
    override val upgradedAt: Instant? = null,
    override val error: Throwable? = null,
) : UpgradeRepo.Info

/**
 * Hot upgrade info source. The gates suspend waiting for a later emission, so a finite `flowOf`
 * would complete instead of waiting and push them down their fail-open path.
 */
fun fakeUpgradeInfos(
    initial: UpgradeRepo.Info = FakeUpgradeInfo(),
): MutableStateFlow<UpgradeRepo.Info> = MutableStateFlow(initial)

fun mockUpgradeRepo(
    infos: MutableStateFlow<UpgradeRepo.Info> = fakeUpgradeInfos(),
): UpgradeRepo = mockk<UpgradeRepo>(relaxed = true).apply {
    every { upgradeInfo } returns infos
}
