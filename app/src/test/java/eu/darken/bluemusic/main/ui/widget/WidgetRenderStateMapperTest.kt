package eu.darken.bluemusic.main.ui.widget

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class WidgetRenderStateMapperTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val themeWithColors = WidgetTheme(
        backgroundColor = Color.rgb(20, 20, 20),
        foregroundColor = Color.rgb(240, 240, 240),
        backgroundAlpha = 200,
    )

    private fun btState(
        enabled: Boolean = true,
        hasPermission: Boolean = true,
    ) = BluetoothRepo.State(
        isEnabled = enabled,
        hasPermission = hasPermission,
        devices = emptySet(),
    )

    private fun buildDevice(
        address: String,
        type: SourceDevice.Type = SourceDevice.Type.HEADPHONES,
        connected: Boolean = true,
        enabled: Boolean = true,
        volumeLock: Boolean = false,
        customName: String? = null,
        label: String = "Buds",
    ): ManagedDevice {
        val sourceDevice = mockk<SourceDevice>(relaxed = true).apply {
            every { this@apply.address } returns address
            every { this@apply.deviceType } returns type
            every { this@apply.label } returns label
        }
        val config = DeviceConfigEntity(
            address = address,
            customName = customName,
            volumeLock = volumeLock,
            isEnabled = enabled,
        )
        return ManagedDevice(
            isConnected = connected,
            device = sourceDevice,
            config = config,
        )
    }

    @Test
    fun `bluetooth off renders BluetoothOff`() {
        val state = WidgetRenderStateMapper.map(
            context = context,
            btState = btState(enabled = false),
            devices = emptyList(),
            theme = themeWithColors,
            isPro = true,
        )
        state.shouldBeInstanceOf<WidgetRenderState.BluetoothOff>()
    }

    @Test
    fun `no active devices renders NoDevice`() {
        val state = WidgetRenderStateMapper.map(
            context = context,
            btState = btState(),
            devices = listOf(buildDevice("AA:AA:AA:AA:AA:AA", connected = false)),
            theme = themeWithColors,
            isPro = true,
        )
        state.shouldBeInstanceOf<WidgetRenderState.NoDevice>()
    }

    @Test
    fun `phone speaker alone does not produce active state`() {
        val state = WidgetRenderStateMapper.map(
            context = context,
            btState = btState(),
            devices = listOf(
                buildDevice("00:00:00:00:00:00", type = SourceDevice.Type.PHONE_SPEAKER, label = "Phone"),
            ),
            theme = themeWithColors,
            isPro = true,
        )
        state.shouldBeInstanceOf<WidgetRenderState.NoDevice>()
    }

    @Test
    fun `non-pro user with active device renders UpgradeRequired`() {
        val state = WidgetRenderStateMapper.map(
            context = context,
            btState = btState(),
            devices = listOf(buildDevice("AA:AA:AA:AA:AA:AA", customName = "Pixel Buds")),
            theme = themeWithColors,
            isPro = false,
        )
        state.shouldBeInstanceOf<WidgetRenderState.UpgradeRequired>()
        (state as WidgetRenderState.UpgradeRequired).deviceLabel shouldBe "Pixel Buds"
    }

    @Test
    fun `pro user with active device renders Active with locked state propagated`() {
        val state = WidgetRenderStateMapper.map(
            context = context,
            btState = btState(),
            devices = listOf(
                buildDevice(
                    address = "AA:AA:AA:AA:AA:AA",
                    customName = "Buds 2",
                    volumeLock = true,
                )
            ),
            theme = themeWithColors,
            isPro = true,
        )
        state.shouldBeInstanceOf<WidgetRenderState.Active>()
        val active = state as WidgetRenderState.Active
        active.deviceAddress shouldBe "AA:AA:AA:AA:AA:AA"
        active.deviceLabel shouldBe "Buds 2"
        active.isLocked shouldBe true
    }

    @Test
    fun `resolvedSecondaryTextColor blends bg and text with 55 percent ratio`() {
        val bg = Color.BLACK
        val text = Color.WHITE
        val blended = WidgetRenderStateMapper.resolvedSecondaryTextColor(bg, text)
        val expectedChannel = (Color.red(bg) + (Color.red(text) - Color.red(bg)) * 0.55f).toInt()
        Color.red(blended) shouldBe expectedChannel
    }

    @Test
    fun `theme with only background color still resolves without blending accent`() {
        val theme = WidgetTheme(
            backgroundColor = Color.rgb(10, 10, 10),
            foregroundColor = null,
        )
        WidgetRenderStateMapper.resolvedAccentColor(context, theme)
    }

    @Test
    fun `theme with only foreground color still resolves without blending accent`() {
        val theme = WidgetTheme(
            backgroundColor = null,
            foregroundColor = Color.rgb(240, 240, 240),
        )
        WidgetRenderStateMapper.resolvedAccentColor(context, theme)
    }
}
