package eu.darken.bluemusic.upgrade.core

import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class UpgradeRepoFossTest : BaseTest() {

    @Test
    fun `upgrade info maps the pro status and the foss type`() {
        UpgradeRepoFoss.Info(
            isPro = false,
            upgradedAt = null,
        ).apply {
            type shouldBe UpgradeRepo.Type.FOSS
            isPro shouldBe false
            // A local cache read is always conclusive: nothing is pending confirmation.
            isSettled shouldBe true
        }

        UpgradeRepoFoss.Info(
            isPro = true,
            upgradedAt = Instant.EPOCH,
            fossUpgradeType = FossUpgrade.Type.GITHUB_SPONSORS,
        ).apply {
            isPro shouldBe true
            upgradedAt shouldBe Instant.EPOCH
            fossUpgradeType shouldBe FossUpgrade.Type.GITHUB_SPONSORS
        }
    }
}
