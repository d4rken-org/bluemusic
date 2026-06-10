package eu.darken.bluemusic.monitor.core.service

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class BluetoothEventQueueTest : BaseTest() {

    private fun event(sequence: Long) = BluetoothEventQueue.Event(
        type = BluetoothEventQueue.Event.Type.CONNECTED,
        sourceDevice = mockk<SourceDevice> {
            every { address } returns "AA:BB:CC:DD:EE:FF"
        },
        receivedAtElapsedMs = sequence * 1000L,
        sequence = sequence,
    )

    @Test
    fun `clear drops all queued events`() = runTest {
        val queue = BluetoothEventQueue()
        queue.submit(event(0))
        queue.submit(event(1))
        queue.submit(event(2))

        queue.clear()

        val received = mutableListOf<BluetoothEventQueue.Event>()
        val collector = launch { received.add(queue.events.first()) }
        advanceTimeBy(5_000)
        collector.isActive shouldBe true // nothing to receive, collector still suspended
        received shouldBe emptyList()
        collector.cancel()
    }

    @Test
    fun `events submitted after clear are still delivered`() = runTest {
        val queue = BluetoothEventQueue()
        queue.submit(event(0))
        queue.clear()
        queue.submit(event(1))

        queue.events.first().sequence shouldBe 1L
    }
}
