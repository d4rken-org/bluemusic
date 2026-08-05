package eu.darken.bluemusic.main.ui.settings.support

import eu.darken.bluemusic.common.debug.recorder.core.DebugSession
import eu.darken.bluemusic.common.debug.recorder.core.DebugSessionManager
import eu.darken.bluemusic.common.debug.recorder.core.RecorderModule
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.IOException

/**
 * Starting a debug recording can fail — and used to fail silently: the module rethrew into its own
 * scope instead of answering the caller, so this ViewModel's launch never completed and the user
 * kept staring at a toggle that did nothing. The failure has to arrive at [errorEvents], which is
 * what the screen renders its error overlay from.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SupportScreenViewModelTest : BaseTest() {

    private val sessionManager: DebugSessionManager = mockk(relaxed = true)

    private fun TestScope.viewModel(): SupportScreenViewModel {
        every { sessionManager.recorderState } returns flowOf(RecorderModule.State())
        every { sessionManager.sessions } returns flowOf(emptyList<DebugSession>())
        return SupportScreenViewModel(
            dispatcherProvider = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
            navCtrl = mockk(relaxed = true),
            webpageTool = mockk(relaxed = true),
            sessionManager = sessionManager,
        )
    }

    @Test
    fun `a failed start reaches the error events instead of vanishing`() = runTest {
        val boom = IOException("recorder broken")
        coEvery { sessionManager.startRecording() } throws boom

        val vm = viewModel()
        val error = async { vm.errorEvents.first() }
        runCurrent()

        vm.startDebugLog()
        advanceUntilIdle()

        error.await() shouldBe boom
    }
}
