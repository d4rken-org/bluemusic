package eu.darken.bluemusic.devices.core

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.SourceDeviceWrapper
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.upgrade.FakeUpgradeInfo
import testhelpers.upgrade.fakeUpgradeInfos
import testhelpers.upgrade.mockUpgradeRepo

@OptIn(ExperimentalCoroutinesApi::class)
class VolumeLimitToggleTest : BaseTest() {

    private val address = "AA:BB:CC:DD:EE:FF"

    private fun managedDevice(config: DeviceConfigEntity) = ManagedDevice(
        isConnected = true,
        device = SourceDeviceWrapper(
            address = address,
            alias = "Test Device",
            name = "Test Device",
            deviceType = SourceDevice.Type.HEADPHONES,
            isConnected = true,
        ),
        config = config,
    )

    private fun deviceRepo(
        config: DeviceConfigEntity = DeviceConfigEntity(address = address),
        managed: Boolean = true,
    ): DeviceRepo = mockk<DeviceRepo>(relaxed = true).apply {
        coEvery { isManaged(address) } returns managed
        every { devices } returns MutableStateFlow(listOf(managedDevice(config)))
    }

    @Test
    fun `a settled non-pro user cannot enable the limit`() = runTest {
        val repo = deviceRepo()
        val upgradeRepo = mockUpgradeRepo(fakeUpgradeInfos(FakeUpgradeInfo(isPro = false, isSettled = true)))

        repo.toggleVolumeLimit(address, upgradeRepo) shouldBe ToggleResult.NOT_PRO
        coVerify(exactly = 0) { repo.updateDevice(any(), any()) }
    }

    @Test
    fun `a non-pro user can always disable the limit`() = runTest {
        val repo = deviceRepo(DeviceConfigEntity(address = address, volumeLimit = true))
        val upgradeRepo = mockUpgradeRepo(fakeUpgradeInfos(FakeUpgradeInfo(isPro = false, isSettled = true)))

        repo.toggleVolumeLimit(address, upgradeRepo) shouldBe ToggleResult.SUCCESS
        coVerify { repo.updateDevice(address, any()) }
    }

    @Test
    fun `an unmanaged device is reported as such for a pro user`() = runTest {
        val repo = deviceRepo(managed = false)
        val upgradeRepo = mockUpgradeRepo(fakeUpgradeInfos(FakeUpgradeInfo(isPro = true, isSettled = true)))

        repo.toggleVolumeLimit(address, upgradeRepo) shouldBe ToggleResult.NOT_MANAGED
    }

    @Test
    fun `a pro user flips the flag`() = runTest {
        val repo = deviceRepo()
        val upgradeRepo = mockUpgradeRepo(fakeUpgradeInfos(FakeUpgradeInfo(isPro = true, isSettled = true)))
        val update = slot<(DeviceConfigEntity) -> DeviceConfigEntity>()
        coEvery { repo.updateDevice(address, capture(update)) } returns Unit

        repo.toggleVolumeLimit(address, upgradeRepo) shouldBe ToggleResult.SUCCESS

        update.captured(DeviceConfigEntity(address = address)).volumeLimit shouldBe true
        update.captured(DeviceConfigEntity(address = address, volumeLimit = true)).volumeLimit shouldBe false
    }

    @Test
    fun `setVolumeLimit writes both bounds of the given stream`() = runTest {
        val repo = deviceRepo()
        val update = slot<(DeviceConfigEntity) -> DeviceConfigEntity>()
        coEvery { repo.updateDevice(address, capture(update)) } returns Unit

        repo.setVolumeLimit(address, AudioStream.Type.MUSIC, min = 0.1f, max = 0.5f)

        val updated = update.captured(DeviceConfigEntity(address = address))
        updated.musicVolumeMin shouldBe 0.1f
        updated.musicVolumeMax shouldBe 0.5f
    }

    @Test
    fun `setVolumeLimit clears a bound with null`() = runTest {
        val repo = deviceRepo()
        val update = slot<(DeviceConfigEntity) -> DeviceConfigEntity>()
        coEvery { repo.updateDevice(address, capture(update)) } returns Unit

        repo.setVolumeLimit(address, AudioStream.Type.ALARM, min = null, max = null)

        val updated = update.captured(
            DeviceConfigEntity(address = address, alarmVolumeMin = 0.2f, alarmVolumeMax = 0.4f)
        )
        updated.alarmVolumeMin shouldBe null
        updated.alarmVolumeMax shouldBe null
    }

    @Test
    fun `setVolumeLimit rejects bounds outside the percentage range`() = runTest {
        val repo = deviceRepo()

        shouldThrow<IllegalArgumentException> {
            repo.setVolumeLimit(address, AudioStream.Type.MUSIC, min = -0.1f, max = null)
        }
        shouldThrow<IllegalArgumentException> {
            repo.setVolumeLimit(address, AudioStream.Type.MUSIC, min = null, max = 1.5f)
        }
        coVerify(exactly = 0) { repo.updateDevice(any(), any()) }
    }

    @Test
    fun `setVolumeLimit rejects non-finite bounds`() = runTest {
        val repo = deviceRepo()

        shouldThrow<IllegalArgumentException> {
            repo.setVolumeLimit(address, AudioStream.Type.MUSIC, min = Float.NaN, max = null)
        }
        shouldThrow<IllegalArgumentException> {
            repo.setVolumeLimit(address, AudioStream.Type.MUSIC, min = null, max = Float.POSITIVE_INFINITY)
        }
        coVerify(exactly = 0) { repo.updateDevice(any(), any()) }
    }

    @Test
    fun `setVolumeLimit rejects an inverted band`() = runTest {
        val repo = deviceRepo()

        shouldThrow<IllegalArgumentException> {
            repo.setVolumeLimit(address, AudioStream.Type.MUSIC, min = 0.6f, max = 0.4f)
        }
        coVerify(exactly = 0) { repo.updateDevice(any(), any()) }
    }
}
