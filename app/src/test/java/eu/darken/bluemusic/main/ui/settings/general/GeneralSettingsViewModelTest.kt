package eu.darken.bluemusic.main.ui.settings.general

import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.theming.ThemeColor
import eu.darken.bluemusic.common.theming.ThemeMode
import eu.darken.bluemusic.common.theming.ThemeStyle
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.main.core.GeneralSettings
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.datastore.FakeDataStoreValue
import testhelpers.upgrade.FakeUpgradeInfo
import testhelpers.upgrade.fakeUpgradeInfos
import testhelpers.upgrade.mockUpgradeRepo

@OptIn(ExperimentalCoroutinesApi::class)
class GeneralSettingsViewModelTest : BaseTest() {

    private lateinit var navCtrl: NavigationController

    private fun TestScope.viewModel(infos: MutableStateFlow<UpgradeRepo.Info>): GeneralSettingsViewModel {
        navCtrl = mockk(relaxed = true)
        val settings = mockk<GeneralSettings>(relaxed = true).apply {
            every { themeMode } returns FakeDataStoreValue(ThemeMode.SYSTEM).mock
            every { themeStyle } returns FakeDataStoreValue(ThemeStyle.DEFAULT).mock
            every { themeColor } returns FakeDataStoreValue(ThemeColor.BLUE).mock
        }
        return GeneralSettingsViewModel(
            dispatcherProvider = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
            navCtrl = navCtrl,
            generalSettings = settings,
            localeManager = mockk(relaxed = true),
            upgradeRepo = mockUpgradeRepo(infos),
        )
    }

    @Test
    fun `a settled free user tapping a theme row lands on the upgrade screen`() = runTest {
        val vm = viewModel(fakeUpgradeInfos(FakeUpgradeInfo(isPro = false, isSettled = true)))

        vm.onThemeRowClicked(GeneralSettingsDialog.THEME_MODE)
        advanceUntilIdle()

        verify { navCtrl.goTo(Nav.Main.Upgrade(), any(), any()) }
    }

    @Test
    fun `a pro user tapping a theme row opens the picker`() = runTest {
        val vm = viewModel(fakeUpgradeInfos(FakeUpgradeInfo(isPro = true, isSettled = true)))

        val event = async { vm.events.first() }
        runCurrent()
        vm.onThemeRowClicked(GeneralSettingsDialog.THEME_STYLE)
        advanceUntilIdle()

        event.await() shouldBe GeneralSettingsDialog.THEME_STYLE
        verify(exactly = 0) { navCtrl.goTo(Nav.Main.Upgrade(), any(), any()) }
    }

    @Test
    fun `a pro user tapping while billing is still settling is not sent to the upgrade screen`() = runTest {
        val infos = fakeUpgradeInfos(FakeUpgradeInfo(isPro = false, isSettled = false))
        val vm = viewModel(infos)

        val event = async { vm.events.first() }
        runCurrent()
        vm.onThemeRowClicked(GeneralSettingsDialog.THEME_COLOR)
        // Suspend inside the gate's wait window without burning its timeout.
        runCurrent()
        infos.value = FakeUpgradeInfo(isPro = true, isSettled = true)
        advanceUntilIdle()

        event.await() shouldBe GeneralSettingsDialog.THEME_COLOR
        verify(exactly = 0) { navCtrl.goTo(Nav.Main.Upgrade(), any(), any()) }
    }
}
