package eu.darken.bluemusic.upgrade.ui

import com.android.billingclient.api.Purchase
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.upgrade.core.OurSku
import eu.darken.bluemusic.upgrade.core.UpgradeRepoGplay
import eu.darken.bluemusic.upgrade.core.billing.PurchasedSku
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
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
        every { autoRestoreBusy } returns MutableStateFlow(false)
        every { isSettled } returns MutableStateFlow(true)
        every { lastProStateAt } returns MutableStateFlow(0L)
        coEvery { querySkus(*anyVararg()) } returns emptyList()
        coEvery { queryCurrentSubscriptions() } returns emptyList()
    }

    private fun buildVm(repo: UpgradeRepoGplay, manage: Boolean = false) = UpgradeViewModel(
        manage = manage,
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
        navCtrl = mockk<NavigationController>(relaxed = true),
        upgradeRepo = repo,
        webpageTool = mockk(relaxed = true),
    )

    private suspend fun UpgradeViewModel.firstLoaded(): UpgradeUiState.Loaded =
        state.filterNotNull().filterIsInstance<UpgradeUiState.Loaded>().first()

    @Test fun `previously pro without current entitlement raises the banner flag`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        every { repo.wasEverPro } returns MutableStateFlow(true)
        val vm = buildVm(repo)

        val state = async { vm.firstLoaded() }
        advanceUntilIdle()

        state.await().wasPreviouslyPro shouldBe true
    }

    @Test fun `grace period keeps the banner flag off`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        every { repo.wasEverPro } returns MutableStateFlow(true)
        every { repo.upgradeInfo } returns
            MutableStateFlow(UpgradeRepoGplay.Info(gracePeriod = true, billingData = null))
        val vm = buildVm(repo)

        val state = async { vm.firstLoaded() }
        advanceUntilIdle()

        state.await().wasPreviouslyPro shouldBe false
    }

    @Test fun `grace stays in the quiet stage before 24h`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns
            MutableStateFlow(UpgradeRepoGplay.Info(gracePeriod = true, billingData = null))
        // Confirmed Pro only an hour ago -> diagnostics not yet.
        every { repo.lastProStateAt } returns MutableStateFlow(System.currentTimeMillis() - 3_600_000L)
        val vm = buildVm(repo)

        val state = async { vm.firstLoaded() }
        advanceUntilIdle()

        state.await().grace?.showDiagnostics shouldBe false
    }

    @Test fun `grace shows diagnostics after 24h`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns
            MutableStateFlow(UpgradeRepoGplay.Info(gracePeriod = true, billingData = null))
        // Last confirmed 25h ago.
        every { repo.lastProStateAt } returns MutableStateFlow(System.currentTimeMillis() - 90_000_000L)
        val vm = buildVm(repo)

        val state = async { vm.firstLoaded() }
        advanceUntilIdle()

        state.await().grace?.showDiagnostics shouldBe true
    }

    @Test fun `iap gate blocks when a subscription is still renewing`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        val renewingSub = mockk<Purchase> { every { isAutoRenewing } returns true }
        coEvery { repo.queryCurrentSubscriptions() } returns listOf(renewingSub)
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.onGoIap(mockk(relaxed = true))
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.SubscriptionStillRenewing
        verify(exactly = 0) { repo.launchBillingFlow(any(), any(), any(), any()) }
    }

    @Test fun `iap gate launches when there is no renewing subscription`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        coEvery { repo.queryCurrentSubscriptions() } returns emptyList()
        val vm = buildVm(repo)

        vm.onGoIap(mockk(relaxed = true))
        advanceUntilIdle()

        verify(exactly = 1) { repo.launchBillingFlow(any(), OurSku.Iap.PRO_UPGRADE, null, any()) }
    }

    @Test fun `iap gate fails closed on a check timeout`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.queryCurrentSubscriptions() } coAnswers {
            delay(11_000) // longer than the 10s verify timeout
            emptyList()
        }
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.onGoIap(mockk(relaxed = true))
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.SubscriptionCheckFailed
        verify(exactly = 0) { repo.launchBillingFlow(any(), any(), any(), any()) }
    }

    @Test fun `iap gate fails closed on a check error`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.queryCurrentSubscriptions() } throws RuntimeException("Play down")
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.onGoIap(mockk(relaxed = true))
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.SubscriptionCheckFailed
        verify(exactly = 0) { repo.launchBillingFlow(any(), any(), any(), any()) }
    }

    @Test fun `restore is ignored while a verification is in progress`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        coEvery { repo.queryCurrentSubscriptions() } coAnswers {
            delay(5_000)
            emptyList()
        }
        val vm = buildVm(repo)

        vm.onGoIap(mockk(relaxed = true)) // holds the verifying guard through the SUBS check
        advanceTimeBy(1_000)
        vm.restorePurchase() // must be rejected while verifying
        advanceUntilIdle()

        coVerify(exactly = 0) { repo.restorePurchaseNow() }
    }

    @Test fun `iap verification is ignored while a restore is in progress`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } coAnswers {
            delay(5_000)
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        }
        val vm = buildVm(repo)

        vm.restorePurchase() // holds the restoring guard
        advanceTimeBy(1_000)
        vm.onGoIap(mockk(relaxed = true)) // must be rejected while restoring
        advanceUntilIdle()

        coVerify(exactly = 0) { repo.queryCurrentSubscriptions() }
        verify(exactly = 0) { repo.launchBillingFlow(any(), any(), any(), any()) }
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

    @Test fun `restore with an owned purchase emits RestoreSucceeded`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        val owned = mockk<UpgradeRepoGplay.Info> {
            every { upgrades } returns listOf(mockk<PurchasedSku>())
            every { isPro } returns true
        }
        coEvery { repo.restorePurchaseNow() } returns owned
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.RestoreSucceeded
    }

    @Test fun `restore that only reaches grace emits RestoreFailed`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        // grace-only Info has no owned purchases.
        coEvery { repo.restorePurchaseNow() } returns UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.RestoreFailed
    }

    @Test fun `restore pads to a minimum visible duration`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        // Warm cache: answers instantly.
        coEvery { repo.restorePurchaseNow() } returns
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        val vm = buildVm(repo)

        var fired = false
        val collector = launch {
            vm.events.first()
            fired = true
        }

        vm.restorePurchase()
        advanceTimeBy(1_000)
        // The result event must not fire before the 1.5s min-visible pad elapses.
        fired shouldBe false
        advanceTimeBy(1_000)
        fired shouldBe true

        collector.cancel()
    }

    @Test fun `a restore error still waits the minimum visible duration`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } throws RuntimeException("Play down")
        val vm = buildVm(repo)

        var fired = false
        val collector = launch {
            vm.errorEvents.first()
            fired = true
        }

        vm.restorePurchase()
        advanceTimeBy(1_000)
        // The error must not surface before the 1.5s min-visible pad (previously a throw skipped it).
        fired shouldBe false
        advanceTimeBy(1_000)
        fired shouldBe true

        collector.cancel()
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

        val states = mutableListOf<UpgradeUiState.Loaded>()
        val collector = launch {
            vm.state.filterNotNull().filterIsInstance<UpgradeUiState.Loaded>().collect { states.add(it) }
        }

        vm.restorePurchase()
        advanceTimeBy(5_000)
        states.last().restoreInProgress shouldBe true

        advanceUntilIdle()
        states.last().restoreInProgress shouldBe false

        collector.cancel()
    }

    @Test fun `auto-restore busy state pauses the ui like a manual restore`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        every { repo.autoRestoreBusy } returns MutableStateFlow(true)
        val vm = buildVm(repo)

        val state = async { vm.firstLoaded() }
        advanceUntilIdle()

        state.await().restoreInProgress shouldBe true
    }

    @Test fun `both price queries failing yields the Unavailable state`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        coEvery { repo.querySkus(*anyVararg()) } coAnswers {
            delay(16_000) // exceeds the 15s query timeout -> null -> Unavailable
            emptyList()
        }
        val vm = buildVm(repo)

        val state = async {
            vm.state.filterNotNull().filterIsInstance<UpgradeUiState.Unavailable>().first()
        }
        advanceUntilIdle()

        state.await() // resolves only if the Unavailable state was emitted
    }

    @Test fun `onRetry re-runs the failed price query`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        var calls = 0
        coEvery { repo.querySkus(*anyVararg()) } coAnswers {
            calls++
            throw RuntimeException("Play down")
        }
        val vm = buildVm(repo)

        val collector = launch { vm.state.collect {} }
        advanceUntilIdle()
        val afterFirst = calls // iap + sub

        vm.onRetry()
        advanceUntilIdle()

        (calls > afterFirst) shouldBe true
        collector.cancel()
    }

    @Test fun `onContactSupport navigates to the contact screen`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        val navCtrl = mockk<NavigationController>(relaxed = true)
        val vm = UpgradeViewModel(
            manage = true,
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            navCtrl = navCtrl,
            upgradeRepo = repo,
            webpageTool = mockk(relaxed = true),
        )

        vm.onContactSupport()
        advanceUntilIdle()

        verify { navCtrl.goTo(Nav.Settings.ContactSupport, null, false) }
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

        vm.restorePurchase()
        advanceUntilIdle()
        coVerify(exactly = 2) { repo.restorePurchaseNow() }
    }
}
