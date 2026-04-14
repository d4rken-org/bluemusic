package eu.darken.bluemusic.monitor.core.service

import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.modules.VolumeModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class VolumeEventDispatcherTest : BaseTest() {

    private val testEvent = VolumeEvent(
        streamId = AudioStream.Id.STREAM_MUSIC,
        oldVolume = 5,
        newVolume = 10,
        self = false,
    )

    private fun module(priority: Int, tag: String): VolumeModule = mockk(relaxed = true) {
        every { this@mockk.priority } returns priority
        every { this@mockk.tag } returns tag
    }

    @Test
    fun `modules are executed in priority order`() = runTest {
        val executionOrder = mutableListOf<String>()

        val high = module(1, "High")
        coEvery { high.handle(any()) } coAnswers { executionOrder.add("high") }

        val low = module(10, "Low")
        coEvery { low.handle(any()) } coAnswers { executionOrder.add("low") }

        val dispatcher = VolumeEventDispatcher(setOf(low, high))

        dispatcher.dispatch(testEvent)

        executionOrder shouldBe listOf("high", "low")
    }

    @Test
    fun `same-priority modules run in parallel`() = runTest {
        val moduleA = module(10, "A")
        val moduleB = module(10, "B")

        val dispatcher = VolumeEventDispatcher(setOf(moduleA, moduleB))

        dispatcher.dispatch(testEvent)

        coVerify(exactly = 1) { moduleA.handle(testEvent) }
        coVerify(exactly = 1) { moduleB.handle(testEvent) }
    }

    @Test
    fun `module exception does not break other modules at same priority`() = runTest {
        val failing = module(10, "Failing")
        coEvery { failing.handle(any()) } throws RuntimeException("boom")

        val healthy = module(10, "Healthy")

        val dispatcher = VolumeEventDispatcher(setOf(failing, healthy))

        dispatcher.dispatch(testEvent)

        coVerify(exactly = 1) { healthy.handle(testEvent) }
    }

    @Test
    fun `CancellationException is rethrown`() = runTest {
        val cancelling = module(10, "Cancelling")
        coEvery { cancelling.handle(any()) } throws CancellationException("cancelled")

        val dispatcher = VolumeEventDispatcher(setOf(cancelling))

        shouldThrow<CancellationException> {
            dispatcher.dispatch(testEvent)
        }
    }

    @Test
    fun `empty module set is a no-op`() = runTest {
        val dispatcher = VolumeEventDispatcher(emptySet())
        dispatcher.dispatch(testEvent)
    }
}
