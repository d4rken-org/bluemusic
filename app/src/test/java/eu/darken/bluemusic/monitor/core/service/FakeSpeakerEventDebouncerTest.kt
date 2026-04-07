package eu.darken.bluemusic.monitor.core.service

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.speaker.SpeakerDeviceProvider
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.ManagedDevice
import io.mockk.Called
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.datastore.FakeDataStoreValue
import java.time.Duration

class FakeSpeakerEventDebouncerTest : BaseTest() {

    private lateinit var eventQueue: BluetoothEventQueue
    private lateinit var deviceRepo: DeviceRepo
    private lateinit var devicesSettings: DevicesSettings
    private lateinit var speakerDeviceProvider: SpeakerDeviceProvider
    private lateinit var fakeIsEnabled: FakeDataStoreValue<Boolean>
    private lateinit var devicesFlow: MutableStateFlow<List<ManagedDevice>>

    private val speakerAddress = "00:00:00:00:00:00"
    private val realDeviceAddress = "AA:BB:CC:DD:EE:FF"

    private val fakeEvent = BluetoothEventQueue.Event(
        type = BluetoothEventQueue.Event.Type.CONNECTED,
        sourceDevice = mockk {
            every { address } returns speakerAddress
            every { deviceType } returns SourceDevice.Type.PHONE_SPEAKER
        },
    )
    private val secondFakeEvent = BluetoothEventQueue.Event(
        type = BluetoothEventQueue.Event.Type.CONNECTED,
        sourceDevice = mockk {
            every { address } returns speakerAddress
            every { deviceType } returns SourceDevice.Type.PHONE_SPEAKER
        },
    )

    private val debounce: Duration = Duration.ofSeconds(3)

    @BeforeEach
    fun setup() {
        eventQueue = mockk(relaxed = true)
        deviceRepo = mockk()
        devicesSettings = mockk()
        speakerDeviceProvider = mockk()
        fakeIsEnabled = FakeDataStoreValue(initial = true)
        devicesFlow = MutableStateFlow(emptyList())

        every { devicesSettings.isEnabled } returns fakeIsEnabled.mock
        every { speakerDeviceProvider.address } returns speakerAddress
        every { deviceRepo.devices } returns devicesFlow
    }

    private fun TestScope.createDebouncer() = FakeSpeakerEventDebouncer(
        appScope = backgroundScope,
        eventQueue = eventQueue,
        deviceRepo = deviceRepo,
        devicesSettings = devicesSettings,
        speakerDeviceProvider = speakerDeviceProvider,
    )

    private fun managedDevice(
        address: String,
        connected: Boolean,
    ): ManagedDevice = mockk {
        every { this@mockk.address } returns address
        every { isConnected } returns connected
    }

    @Test
    fun `schedule and pass debounce - submits event once`() = runTest {
        val debouncer = createDebouncer()

        debouncer.scheduleFakeSpeakerConnect(fakeEvent, debounce)

        advanceTimeBy(debounce.toMillis() + 100)
        runCurrent()

        coVerify(exactly = 1) { eventQueue.submit(fakeEvent) }
    }

    @Test
    fun `cancel before debounce - event not submitted`() = runTest {
        val debouncer = createDebouncer()

        debouncer.scheduleFakeSpeakerConnect(fakeEvent, debounce)
        advanceTimeBy(debounce.toMillis() / 2)
        runCurrent()

        debouncer.cancelPendingFakeSpeakerConnect()

        advanceTimeBy(debounce.toMillis() * 2)
        runCurrent()

        coVerify(exactly = 0) { eventQueue.submit(any()) }
    }

    @Test
    fun `schedule twice in a row - only the second event submitted`() = runTest {
        val debouncer = createDebouncer()

        debouncer.scheduleFakeSpeakerConnect(fakeEvent, debounce)
        debouncer.scheduleFakeSpeakerConnect(secondFakeEvent, debounce)

        advanceTimeBy(debounce.toMillis() + 100)
        runCurrent()

        coVerify(exactly = 0) { eventQueue.submit(fakeEvent) }
        coVerify(exactly = 1) { eventQueue.submit(secondFakeEvent) }
    }

    @Test
    fun `cancel with no pending - no error and no submission`() = runTest {
        val debouncer = createDebouncer()

        debouncer.cancelPendingFakeSpeakerConnect()

        advanceTimeBy(debounce.toMillis() * 2)
        runCurrent()

        coVerify { eventQueue wasNot Called }
    }

    @Test
    fun `schedule cancel schedule - second submission goes through`() = runTest {
        val debouncer = createDebouncer()

        debouncer.scheduleFakeSpeakerConnect(fakeEvent, debounce)
        debouncer.cancelPendingFakeSpeakerConnect()
        debouncer.scheduleFakeSpeakerConnect(secondFakeEvent, debounce)

        advanceTimeBy(debounce.toMillis() + 100)
        runCurrent()

        coVerify(exactly = 0) { eventQueue.submit(fakeEvent) }
        coVerify(exactly = 1) { eventQueue.submit(secondFakeEvent) }
    }

    @Test
    fun `state check drops submission when real device is active`() = runTest {
        devicesFlow.value = listOf(managedDevice(realDeviceAddress, connected = true))
        val debouncer = createDebouncer()

        debouncer.scheduleFakeSpeakerConnect(fakeEvent, debounce)

        advanceTimeBy(debounce.toMillis() + 100)
        runCurrent()

        coVerify(exactly = 0) { eventQueue.submit(any()) }
    }

    @Test
    fun `state check drops submission when BVM disabled`() = runTest {
        fakeIsEnabled.value = false
        val debouncer = createDebouncer()

        debouncer.scheduleFakeSpeakerConnect(fakeEvent, debounce)

        advanceTimeBy(debounce.toMillis() + 100)
        runCurrent()

        coVerify(exactly = 0) { eventQueue.submit(any()) }
    }

    @Test
    fun `race - cancel and schedule interleaved with active device drops event`() = runTest {
        devicesFlow.value = listOf(managedDevice(realDeviceAddress, connected = true))
        val debouncer = createDebouncer()

        backgroundScope.launch {
            debouncer.scheduleFakeSpeakerConnect(fakeEvent, debounce)
        }
        backgroundScope.launch {
            debouncer.cancelPendingFakeSpeakerConnect()
        }

        advanceTimeBy(debounce.toMillis() + 100)
        runCurrent()

        coVerify(exactly = 0) { eventQueue.submit(any()) }
    }
}
