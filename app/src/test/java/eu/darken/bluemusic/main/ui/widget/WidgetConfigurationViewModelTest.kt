package eu.darken.bluemusic.main.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.coroutine.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class WidgetConfigurationViewModelTest {

    private val widgetId = 42
    private lateinit var context: Context
    private lateinit var upgradeRepo: UpgradeRepo
    private lateinit var upgradeInfo: MutableStateFlow<FakeUpgradeInfo>
    private lateinit var appWidgetManager: AppWidgetManager
    private var storedOptions: Bundle = Bundle()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        upgradeInfo = MutableStateFlow(FakeUpgradeInfo(isUpgraded = true))
        upgradeRepo = mockk(relaxed = true)
        every { upgradeRepo.upgradeInfo } returns upgradeInfo

        appWidgetManager = mockk(relaxed = true)
        every { appWidgetManager.getAppWidgetOptions(widgetId) } answers { storedOptions }
        val updated = slot<Bundle>()
        every { appWidgetManager.updateAppWidgetOptions(widgetId, capture(updated)) } answers {
            storedOptions = updated.captured
        }

        io.mockk.mockkStatic(AppWidgetManager::class)
        every { AppWidgetManager.getInstance(any()) } returns appWidgetManager
    }

    @After
    fun tearDown() {
        io.mockk.unmockkStatic(AppWidgetManager::class)
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
        vm.enterCustomMode(Color.RED, Color.BLUE)
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

    private data class FakeUpgradeInfo(
        override val isUpgraded: Boolean,
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS,
        override val upgradedAt: java.time.Instant? = null,
        override val error: Throwable? = null,
    ) : UpgradeRepo.Info
}
