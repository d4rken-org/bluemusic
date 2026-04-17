package eu.darken.bluemusic.main.ui.widget

import android.content.Context
import android.graphics.Color
import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class WidgetRenderStateMapperTest : BaseTest() {

    private lateinit var context: Context
    private lateinit var themeWithColors: WidgetTheme

    @BeforeEach
    fun setup() {
        mockkStatic(Color::class)
        every { Color.rgb(any<Int>(), any<Int>(), any<Int>()) } answers {
            val r = firstArg<Int>() and 0xff
            val g = secondArg<Int>() and 0xff
            val b = thirdArg<Int>() and 0xff
            (0xff shl 24) or (r shl 16) or (g shl 8) or b
        }
        every { Color.argb(any<Int>(), any<Int>(), any<Int>(), any<Int>()) } answers {
            val a = firstArg<Int>() and 0xff
            val r = secondArg<Int>() and 0xff
            val g = thirdArg<Int>() and 0xff
            val b = arg<Int>(3) and 0xff
            (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        every { Color.alpha(any<Int>()) } answers { (firstArg<Int>() ushr 24) and 0xff }
        every { Color.red(any<Int>()) } answers { (firstArg<Int>() shr 16) and 0xff }
        every { Color.green(any<Int>()) } answers { (firstArg<Int>() shr 8) and 0xff }
        every { Color.blue(any<Int>()) } answers { firstArg<Int>() and 0xff }

        context = mockk(relaxed = true)
        themeWithColors = WidgetTheme(
            backgroundColor = Color.rgb(20, 20, 20),
            foregroundColor = Color.rgb(240, 240, 240),
            backgroundAlpha = 200,
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Color::class)
    }

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
        val upgradeRequired = state.shouldBeInstanceOf<WidgetRenderState.UpgradeRequired>()
        upgradeRequired.deviceLabel shouldBe "Pixel Buds"
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
        val active = state.shouldBeInstanceOf<WidgetRenderState.Active>()
        active.deviceAddress shouldBe "AA:AA:AA:AA:AA:AA"
        active.deviceLabel shouldBe "Buds 2"
        active.isLocked shouldBe true
    }

    @Test
    fun `resolvedSecondaryTextColor blends bg and text with 55 percent ratio`() {
        val bg = 0xFF000000.toInt()
        val text = 0xFFFFFFFF.toInt()
        val blended = WidgetRenderStateMapper.resolvedSecondaryTextColor(bg, text)
        val expectedChannel = (Color.red(bg) + (Color.red(text) - Color.red(bg)) * 0.55f).toInt()
        Color.red(blended) shouldBe expectedChannel
    }
}
