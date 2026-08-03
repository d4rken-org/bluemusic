package eu.darken.bluemusic.common.flow

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.util.concurrent.atomic.AtomicInteger

class DynamicStateFlowTest : BaseTest() {

    /**
     * Pre-fix, [DynamicStateFlow.updateBlocking] emitted its update BEFORE subscribing to the
     * shared flow. Under contention its awaited State could be displaced from the replay-1 cache
     * by a successor update before the subscription existed, and the await then never completed
     * (jstack-verified: the pre-fix suite wedged a CI runner until the 6h job timeout). This pins
     * the subscribe-before-emit contract, and the timeout envelope turns any regression into a
     * fast failure instead of another wedged runner.
     */
    @Test
    fun `concurrent blocking updates survive a reactive collector`() {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val hotData = DynamicStateFlow(
                loggingTag = "tag",
                parentScope = scope,
                startValueProvider = { 0 },
            )

            // Mirrors RecorderModule: a collector that reacts to every state with an update of its
            // own, keeping the producer busy between the other callers' updates. The echo updates
            // are value-neutral so the final count stays exact, and capped so the echo terminates.
            val echoes = AtomicInteger(0)
            val subscribed = CompletableDeferred<Unit>()
            scope.launch {
                hotData.flow.collect { value ->
                    subscribed.complete(Unit)
                    if (value % 2 == 1 && echoes.getAndIncrement() < 100) {
                        hotData.updateAsync { this + 0 }
                    }
                }
            }

            runBlocking {
                withTimeout(20_000) {
                    // The contention only means anything with the reactive collector actually
                    // attached: its first received value proves the subscription exists.
                    subscribed.await()

                    val workers = (1..2).map {
                        launch(Dispatchers.IO) {
                            repeat(100) { hotData.updateBlocking { this + 1 } }
                        }
                    }
                    workers.forEach { it.join() }
                    workers.all { it.isCompleted } shouldBe true

                    hotData.flow.first() shouldBe 200
                    // Non-vacuity: without a single echo there was no successor update to displace
                    // an awaited State, and the test would pass for the wrong reason.
                    echoes.get() shouldBeGreaterThan 0
                }
            }
        } finally {
            scope.cancel()
        }
    }
}
