package eu.darken.bluemusic.main.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetConfigurationViewModelTest : BaseTest() {

    private val widgetId = 42
    private lateinit var context: Context
    private lateinit var upgradeRepo: UpgradeRepo
    private lateinit var upgradeInfo: MutableStateFlow<FakeUpgradeInfo>
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var storedOptions: Bundle

    @BeforeEach
    fun setup() {
        mockkStatic(Color::class)
        every { Color.rgb(any<Int>(), any<Int>(), any<Int>()) } answers {
            val r = firstArg<Int>() and 0xff
            val g = secondArg<Int>() and 0xff
            val b = thirdArg<Int>() and 0xff
            (0xff shl 24) or (r shl 16) or (g shl 8) or b
        }
        every { Color.alpha(any<Int>()) } answers { (firstArg<Int>() ushr 24) and 0xff }
        every { Color.red(any<Int>()) } answers { (firstArg<Int>() shr 16) and 0xff }
        every { Color.green(any<Int>()) } answers { (firstArg<Int>() shr 8) and 0xff }
        every { Color.blue(any<Int>()) } answers { firstArg<Int>() and 0xff }

        context = mockk(relaxed = true)
        storedOptions = mapBackedBundle()

        upgradeInfo = MutableStateFlow(FakeUpgradeInfo(isUpgraded = true))
        upgradeRepo = mockk(relaxed = true)
        every { upgradeRepo.upgradeInfo } returns upgradeInfo

        appWidgetManager = mockk(relaxed = true)
        every { appWidgetManager.getAppWidgetOptions(widgetId) } answers { storedOptions }
        val updated = slot<Bundle>()
        every { appWidgetManager.updateAppWidgetOptions(widgetId, capture(updated)) } answers {
            storedOptions = updated.captured
        }

        mockkStatic(AppWidgetManager::class)
        every { AppWidgetManager.getInstance(any()) } returns appWidgetManager
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(AppWidgetManager::class)
        unmockkStatic(Color::class)
    }

    private fun viewModel(
        id: Int = widgetId,
    ): WidgetConfigurationViewModel = WidgetConfigurationViewModel(
        savedStateHandle = SavedStateHandle(mapOf(AppWidgetManager.EXTRA_APPWIDGET_ID to id)),
        dispatcherProvider = TestDispatcherProvider(UnconfinedTestDispatcher()),
        upgradeRepo = upgradeRepo,
        context = context,
    )

    @Test
    fun `invalid widget id falls back to default theme`() = runTest {
        val vm = viewModel(id = AppWidgetManager.INVALID_APPWIDGET_ID)

        val state = vm.state.filterNotNull().first()
        state.theme shouldBe WidgetTheme.DEFAULT
    }

    @Test
    fun `valid widget id loads theme from stored options`() = runTest {
        val preset = WidgetTheme.Preset.CLASSIC_DARK
        WidgetTheme(
            backgroundColor = preset.presetBg,
            foregroundColor = preset.presetFg,
        ).toBundle(storedOptions)

        val vm = viewModel()
        val state = vm.state.filterNotNull().first()

        state.theme.backgroundColor shouldBe preset.presetBg
        state.theme.foregroundColor shouldBe preset.presetFg
        state.activePreset shouldBe preset
    }

    @Test
    fun `selectPreset sets preset and exits custom mode`() = runTest {
        val vm = viewModel()
        vm.selectPreset(WidgetTheme.Preset.MATERIAL_YOU)
        advanceUntilIdle()

        val state = vm.state.filterNotNull().first()
        state.activePreset shouldBe WidgetTheme.Preset.MATERIAL_YOU
        state.isCustomMode shouldBe false
    }

    @Test
    fun `enterCustomMode flips to custom mode with full-alpha colors`() = runTest {
        val vm = viewModel()
        vm.enterCustomMode(resolvedBg = Color.rgb(10, 20, 30), resolvedFg = Color.rgb(200, 210, 220))
        advanceUntilIdle()

        val state = vm.state.filterNotNull().first()
        state.isCustomMode shouldBe true
        Color.alpha(state.theme.backgroundColor ?: 0) shouldBe 0xFF
        Color.alpha(state.theme.foregroundColor ?: 0) shouldBe 0xFF
    }

    @Test
    fun `setBackgroundAlpha coerces out-of-range values`() = runTest {
        val vm = viewModel()
        vm.setBackgroundAlpha(1000)
        advanceUntilIdle()
        vm.state.filterNotNull().first().theme.backgroundAlpha shouldBe 255

        vm.setBackgroundAlpha(-50)
        advanceUntilIdle()
        vm.state.filterNotNull().first().theme.backgroundAlpha shouldBe 0
    }

    @Test
    fun `resetToDefaults restores default theme`() = runTest {
        val vm = viewModel()
        vm.enterCustomMode(Color.rgb(255, 0, 0), Color.rgb(0, 0, 255))
        advanceUntilIdle()

        vm.resetToDefaults()
        advanceUntilIdle()

        val state = vm.state.filterNotNull().first()
        state.theme shouldBe WidgetTheme.DEFAULT
    }

    @Test
    fun `confirmSelection persists theme that round-trips via fromBundle`() = runTest {
        val vm = viewModel()
        vm.selectPreset(WidgetTheme.Preset.CLASSIC_LIGHT)
        advanceUntilIdle()
        vm.setBackgroundAlpha(128)
        advanceUntilIdle()

        vm.confirmSelection()

        verify { appWidgetManager.updateAppWidgetOptions(widgetId, any()) }
        val roundTripped = WidgetTheme.fromBundle(storedOptions)
        roundTripped.backgroundColor shouldBe WidgetTheme.Preset.CLASSIC_LIGHT.presetBg
        roundTripped.foregroundColor shouldBe WidgetTheme.Preset.CLASSIC_LIGHT.presetFg
        roundTripped.backgroundAlpha shouldBe 128
    }

    private fun mapBackedBundle(): Bundle {
        val store = mutableMapOf<String, Any?>()
        val bundle = mockk<Bundle>(relaxed = true)
        every { bundle.putString(any(), any()) } answers {
            store[firstArg()] = secondArg<String?>()
        }
        every { bundle.getString(any()) } answers {
            store[firstArg()] as? String
        }
        every { bundle.getString(any(), any<String>()) } answers {
            (store[firstArg()] as? String) ?: secondArg()
        }
        every { bundle.putInt(any(), any<Int>()) } answers {
            store[firstArg()] = secondArg<Int>()
        }
        every { bundle.getInt(any()) } answers {
            (store[firstArg()] as? Int) ?: 0
        }
        every { bundle.getInt(any(), any<Int>()) } answers {
            (store[firstArg()] as? Int) ?: secondArg()
        }
        every { bundle.putBoolean(any(), any<Boolean>()) } answers {
            store[firstArg()] = secondArg<Boolean>()
        }
        every { bundle.getBoolean(any()) } answers {
            (store[firstArg()] as? Boolean) ?: false
        }
        every { bundle.getBoolean(any(), any<Boolean>()) } answers {
            (store[firstArg()] as? Boolean) ?: secondArg()
        }
        every { bundle.remove(any()) } answers {
            store.remove(firstArg())
        }
        every { bundle.containsKey(any()) } answers {
            firstArg<String>() in store
        }
        return bundle
    }

    private data class FakeUpgradeInfo(
        override val isUpgraded: Boolean,
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS,
        override val upgradedAt: java.time.Instant? = null,
        override val error: Throwable? = null,
    ) : UpgradeRepo.Info
}
