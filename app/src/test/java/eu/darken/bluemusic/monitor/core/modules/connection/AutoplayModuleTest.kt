package eu.darken.bluemusic.monitor.core.modules.connection

import android.media.AudioManager
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.SourceDeviceWrapper
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import eu.darken.bluemusic.monitor.core.modules.SettlePolicy
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration

class AutoplayModuleTest : BaseTest() {

    private val testAddress = "AA:BB:CC:DD:EE:FF"
    private val testSourceDevice = SourceDeviceWrapper(
        address = testAddress,
        alias = "TestDevice",
        name = "TestDevice",
        deviceType = SourceDevice.Type.HEADPHONES,
        isConnected = true,
    )

    private fun device(
        actionDelayMs: Long? = null,
        autoplay: Boolean = true,
        autoplayKeycodes: List<Int> = listOf(android.view.KeyEvent.KEYCODE_MEDIA_PLAY),
    ): ManagedDevice = ManagedDevice(
        isConnected = true,
        device = testSourceDevice,
        config = DeviceConfigEntity(
            address = testAddress,
            isEnabled = true,
            autoplay = autoplay,
            autoplayKeycodes = autoplayKeycodes,
            actionDelay = actionDelayMs,
        ),
    )

    private fun module(): AutoplayModule = AutoplayModule(audioManager = mockk(relaxed = true))

    @Test
    fun `settlePolicy with positive actionDelay returns AfterDeviceSettlePlus`() {
        val mod = module()
        val event = DeviceEvent.Connected(device(actionDelayMs = 4000L))

        val policy = mod.settlePolicy(event)

        policy.shouldBeInstanceOf<SettlePolicy.AfterDeviceSettlePlus>()
        (policy as SettlePolicy.AfterDeviceSettlePlus).extraDelay shouldBe AutoplayModule.APP_READY_EXTRA_DELAY
    }

    @Test
    fun `settlePolicy with actionDelay=0 returns AfterDeviceSettle (no extra wait)`() {
        val mod = module()
        val event = DeviceEvent.Connected(device(actionDelayMs = 0L))

        // User explicitly opted into immediate action; honor that for the extra wait too.
        mod.settlePolicy(event) shouldBe SettlePolicy.AfterDeviceSettle
    }

    @Test
    fun `settlePolicy with default actionDelay returns AfterDeviceSettlePlus`() {
        val mod = module()
        // No explicit override → uses ManagedDevice.defaultActionDelay (6s).
        val event = DeviceEvent.Connected(device(actionDelayMs = null))

        val policy = mod.settlePolicy(event)
        policy.shouldBeInstanceOf<SettlePolicy.AfterDeviceSettlePlus>()
    }

    @Test
    fun `settlePolicy on Disconnected returns AfterDeviceSettle (autoplay only Connected anyway)`() {
        val mod = module()
        val event = DeviceEvent.Disconnected(device(actionDelayMs = 4000L))

        mod.settlePolicy(event) shouldBe SettlePolicy.AfterDeviceSettle
    }

    @Test
    fun `appliesTo Connected with autoplay=true and keycodes returns true`() {
        module().appliesTo(DeviceEvent.Connected(device())) shouldBe true
    }

    @Test
    fun `appliesTo Connected with autoplay=false returns false`() {
        module().appliesTo(DeviceEvent.Connected(device(autoplay = false))) shouldBe false
    }

    @Test
    fun `appliesTo Connected with no keycodes returns false`() {
        module().appliesTo(DeviceEvent.Connected(device(autoplayKeycodes = emptyList()))) shouldBe false
    }

    @Test
    fun `appliesTo Disconnected returns false`() {
        module().appliesTo(DeviceEvent.Disconnected(device())) shouldBe false
    }

    @Test
    fun `APP_READY_EXTRA_DELAY is 2 seconds`() {
        // Regression guard so future tuning is intentional.
        AutoplayModule.APP_READY_EXTRA_DELAY shouldBe Duration.ofSeconds(2)
    }
}
