package eu.darken.bluemusic.devices.core

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.upgrade.FakeUpgradeInfo
import testhelpers.upgrade.fakeUpgradeInfos
import testhelpers.upgrade.mockUpgradeRepo

@OptIn(ExperimentalCoroutinesApi::class)
class VolumeLockToggleTest : BaseTest() {

    private val address = "AA:BB:CC:DD:EE:FF"

    private fun deviceRepo(): DeviceRepo = mockk<DeviceRepo>(relaxed = true).apply {
        coEvery { isManaged(address) } returns true
    }

    @Test
    fun `a settled non-pro user is denied`() = runTest {
        val repo = deviceRepo()
        val upgradeRepo = mockUpgradeRepo(fakeUpgradeInfos(FakeUpgradeInfo(isPro = false, isSettled = true)))

        repo.toggleVolumeLock(address, upgradeRepo) shouldBe ToggleResult.NOT_PRO
        coVerify(exactly = 0) { repo.updateDevice(any(), any()) }
    }

    @Test
    fun `a pro user is not denied while billing is still settling`() = runTest {
        val repo = deviceRepo()
        val infos = fakeUpgradeInfos(FakeUpgradeInfo(isPro = false, isSettled = false))

        val result = async { repo.toggleVolumeLock(address, mockUpgradeRepo(infos)) }
        // Suspend inside the gate's wait window without burning its timeout.
        runCurrent()
        infos.value = FakeUpgradeInfo(isPro = true, isSettled = true)

        result.await() shouldBe ToggleResult.SUCCESS
        coVerify { repo.updateDevice(address, any()) }
    }

    @Test
    fun `an unmanaged device is reported as such for a pro user`() = runTest {
        val repo = mockk<DeviceRepo>(relaxed = true).apply {
            coEvery { isManaged(address) } returns false
        }
        val upgradeRepo = mockUpgradeRepo(fakeUpgradeInfos(FakeUpgradeInfo(isPro = true, isSettled = true)))

        repo.toggleVolumeLock(address, upgradeRepo) shouldBe ToggleResult.NOT_MANAGED
    }
}
