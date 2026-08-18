package eu.darken.bluemusic.eq.core

import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

@OptIn(ExperimentalCoroutinesApi::class)
class EqConfigSaverTest : BaseTest() {

    private val address: DeviceAddr = "AA:BB:CC:DD:EE:FF"

    /** The boost gain of every config the repo was actually told to store, in the order it happened. */
    private val stored = mutableListOf<Int?>()

    private fun deviceRepo(
        gate: CompletableDeferred<Unit>? = null,
        delayFor: (Int?) -> Long = { 0L },
    ): DeviceRepo = mockk<DeviceRepo>(relaxed = true).apply {
        coEvery { updateDevice(any(), any()) } coAnswers {
            gate?.await()
            val update = secondArg<(DeviceConfigEntity) -> DeviceConfigEntity>()
            val updated = update(DeviceConfigEntity(address = firstArg()))
            delayFor(updated.eqBoostGain).takeIf { it > 0 }?.let { delay(it) }
            stored += updated.eqBoostGain
        }
    }

    @Test
    fun `a write survives the scope that submitted it`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val saver = EqConfigSaver(backgroundScope, deviceRepo(gate = gate))

        val write = saver.save(address) { it.copy(eqBoostGain = 400) }

        // What the equalizer screen does: a job waits for the write to sequence the preview clear.
        val callerScope = CoroutineScope(Job() + UnconfinedTestDispatcher(testScheduler))
        callerScope.launch { write.await() }
        runCurrent()

        // The user navigates back while the write is still in the database layer.
        callerScope.cancel()
        runCurrent()

        gate.complete(Unit)
        advanceTimeBy(5_000)
        runCurrent()

        stored shouldBe listOf(400)
    }

    @Test
    fun `two rapid commits are applied in submission order`() = runTest {
        // The first write is the slow one, so anything but a queue would let the second one overtake it.
        val saver = EqConfigSaver(backgroundScope, deviceRepo(delayFor = { gain -> if (gain == 100) 1_000L else 0L }))

        saver.save(address) { it.copy(eqBoostGain = 100) }
        saver.save(address) { it.copy(eqBoostGain = 200) }
        advanceTimeBy(5_000)
        runCurrent()

        stored shouldBe listOf(100, 200)
    }

    @Test
    fun `a failed write is reported to whoever waits for it`() = runTest {
        val repo = mockk<DeviceRepo>(relaxed = true).apply {
            coEvery { updateDevice(any(), any()) } throws IllegalStateException("nope")
        }
        val saver = EqConfigSaver(backgroundScope, repo)

        val write = saver.save(address) { it.copy(eqBoostGain = 400) }
        advanceTimeBy(5_000)
        runCurrent()

        write.isCompleted shouldBe true
        (write.getCompletionExceptionOrNull() is IllegalStateException) shouldBe true

        // The actor keeps going, a failure of one write must not stop later ones.
        val next = saver.save(address) { it.copy(eqBoostGain = 500) }
        advanceTimeBy(5_000)
        runCurrent()
        next.isCompleted shouldBe true
    }
}
