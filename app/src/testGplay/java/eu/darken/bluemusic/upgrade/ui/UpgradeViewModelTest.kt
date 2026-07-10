package eu.darken.bluemusic.upgrade.ui

import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.upgrade.core.UpgradeRepoGplay
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class UpgradeViewModelTest : BaseTest() {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun mockRepo(): UpgradeRepoGplay = mockk<UpgradeRepoGplay>(relaxed = true).apply {
        every { upgradeInfo } returns MutableStateFlow(UpgradeRepoGplay.Info(false, null, null))
        every { wasEverPro } returns MutableStateFlow(false)
        coEvery { querySkus(*anyVararg()) } returns emptyList()
    }

    private fun buildVm(repo: UpgradeRepoGplay) = UpgradeViewModel(
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
        navCtrl = mockk<NavigationController>(relaxed = true),
        upgradeRepo = repo,
    )

    @Test fun `previously pro without current entitlement raises the banner flag`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        every { repo.wasEverPro } returns MutableStateFlow(true)
        val vm = buildVm(repo)

        val state = async { vm.state.filterNotNull().first() }
        advanceUntilIdle()

        state.await().wasPreviouslyPro shouldBe true
    }

    @Test fun `grace period keeps the banner flag off`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        every { repo.wasEverPro } returns MutableStateFlow(true)
        every { repo.upgradeInfo } returns
            MutableStateFlow(UpgradeRepoGplay.Info(gracePeriod = true, billingData = null))
        val vm = buildVm(repo)

        val state = async { vm.state.filterNotNull().first() }
        advanceUntilIdle()

        state.await().wasPreviouslyPro shouldBe false
    }

    @Test fun `restore is single flight`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } coAnswers {
            delay(1_000)
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        }
        val vm = buildVm(repo)

        vm.restorePurchase()
        vm.restorePurchase()
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.restorePurchaseNow() }
    }

    @Test fun `restore guard re-arms after completion`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } returns
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        val vm = buildVm(repo)

        vm.restorePurchase()
        advanceUntilIdle()
        vm.restorePurchase()
        advanceUntilIdle()

        coVerify(exactly = 2) { repo.restorePurchaseNow() }
    }

    @Test fun `restore that times out emits RestoreFailed and re-enables the UI`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } coAnswers {
            delay(30_000) // longer than the 15s restore timeout
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        }
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.RestoreFailed

        // The single-flight guard must have re-armed after the timeout.
        vm.restorePurchase()
        advanceUntilIdle()
        coVerify(exactly = 2) { repo.restorePurchaseNow() }
    }

    @Test fun `restore progress folds into the ui state`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } coAnswers {
            delay(10_000)
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        }
        val vm = buildVm(repo)

        val states = mutableListOf<UpgradeViewModel.State>()
        val collector = launch { vm.state.filterNotNull().collect { states.add(it) } }

        vm.restorePurchase()
        advanceTimeBy(5_000)
        states.last().restoreInProgress shouldBe true

        advanceUntilIdle()
        states.last().restoreInProgress shouldBe false

        collector.cancel()
    }

    @Test fun `restore with no purchase emits RestoreFailed`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } returns UpgradeRepoGplay.Info(false, null, null)
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.RestoreFailed
    }

    @Test fun `restore errors surface via errorEvents not RestoreFailed`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } throws RuntimeException("Play unavailable")
        val vm = buildVm(repo)

        val error = async { vm.errorEvents.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        error.await().message shouldBe "Play unavailable"

        // The single-flight guard must have re-armed after the error.
        vm.restorePurchase()
        advanceUntilIdle()
        coVerify(exactly = 2) { repo.restorePurchaseNow() }
    }
}
