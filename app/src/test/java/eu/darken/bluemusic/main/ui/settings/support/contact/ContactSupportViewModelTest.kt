package eu.darken.bluemusic.main.ui.settings.support.contact

import android.content.Context
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
 * The contact form's own entry into the recorder: a start that cannot succeed used to leave this
 * launch hanging forever, so the consent dialog was answered and then nothing happened at all. The
 * failure has to arrive at [errorEvents], which is what the screen renders its error overlay from.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactSupportViewModelTest : BaseTest() {

    private val sessionManager: DebugSessionManager = mockk(relaxed = true)

    private fun TestScope.viewModel(): ContactSupportViewModel {
        every { sessionManager.recorderState } returns flowOf(RecorderModule.State())
        every { sessionManager.sessions } returns flowOf(emptyList<DebugSession>())
        return ContactSupportViewModel(
            navCtrl = mockk(relaxed = true),
            dispatcherProvider = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
            context = mockk<Context>(relaxed = true),
            sessionManager = sessionManager,
            emailTool = mockk(relaxed = true),
            webpageTool = mockk(relaxed = true),
        )
    }

    @Test
    fun `a failed start reaches the error events instead of vanishing`() = runTest {
        val boom = IOException("recorder broken")
        coEvery { sessionManager.startRecording() } throws boom

        val vm = viewModel()
        val error = async { vm.errorEvents.first() }
        runCurrent()

        vm.doStartRecording()
        advanceUntilIdle()

        error.await() shouldBe boom
    }
}
