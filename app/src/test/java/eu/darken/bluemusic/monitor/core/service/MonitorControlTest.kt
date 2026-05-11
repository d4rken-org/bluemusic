package eu.darken.bluemusic.monitor.core.service

import android.content.Context
import android.content.Intent
import eu.darken.bluemusic.common.startServiceCompat
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.monitor.ui.MonitorNotifications
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

@OptIn(ExperimentalCoroutinesApi::class)
class MonitorControlTest : BaseTest() {

    private lateinit var context: Context
    private lateinit var deviceRepo: DeviceRepo
    private lateinit var monitorNotifications: MonitorNotifications
    private lateinit var devicesFlow: MutableStateFlow<List<ManagedDevice>>
    private lateinit var fakeIntent: Intent

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        monitorNotifications = mockk(relaxed = true)
        devicesFlow = MutableStateFlow(emptyList())
        deviceRepo = mockk(relaxed = true) {
            every { devices } returns devicesFlow
        }

        fakeIntent = mockk(relaxed = true)

        // startServiceCompat is a top-level extension function — mockkStatic the file class.
        mockkStatic("eu.darken.bluemusic.common.ContextExtensionsKt")
        every { context.startServiceCompat(any()) } just Runs

        // MonitorService.intent() uses real android.content.Intent which is a stub in unit tests.
        // Mock the companion to return a fake intent.
        mockkObject(MonitorService.Companion)
        every { MonitorService.intent(any(), any()) } returns fakeIntent
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun device(
        connected: Boolean,
        requiresPersistentSession: Boolean,
    ): ManagedDevice = mockk(relaxed = true) {
        every { isConnected } returns connected
        every { this@mockk.requiresPersistentSession } returns requiresPersistentSession
    }

    @Test
    fun `connected device with requiresPersistentSession triggers service start`() = runTest(UnconfinedTestDispatcher()) {
        MonitorControl(
            appScope = backgroundScope,
            context = context,
            deviceRepo = deviceRepo,
            monitorNotifications = monitorNotifications,
        )

        devicesFlow.value = listOf(device(connected = true, requiresPersistentSession = true))

        verify(atLeast = 1) { context.startServiceCompat(fakeIntent) }
    }

    @Test
    fun `disconnected device does NOT trigger service start`() = runTest(UnconfinedTestDispatcher()) {
        MonitorControl(
            appScope = backgroundScope,
            context = context,
            deviceRepo = deviceRepo,
            monitorNotifications = monitorNotifications,
        )

        devicesFlow.value = listOf(device(connected = false, requiresPersistentSession = true))

        verify(exactly = 0) { context.startServiceCompat(any()) }
    }

    @Test
    fun `connected device without requiresPersistentSession does NOT trigger service start`() = runTest(UnconfinedTestDispatcher()) {
        MonitorControl(
            appScope = backgroundScope,
            context = context,
            deviceRepo = deviceRepo,
            monitorNotifications = monitorNotifications,
        )

        devicesFlow.value = listOf(device(connected = true, requiresPersistentSession = false))

        verify(exactly = 0) { context.startServiceCompat(any()) }
    }

    @Test
    fun `flipping requiresPersistentSession true on connected device triggers service start`() = runTest(UnconfinedTestDispatcher()) {
        MonitorControl(
            appScope = backgroundScope,
            context = context,
            deviceRepo = deviceRepo,
            monitorNotifications = monitorNotifications,
        )

        // First emission: connected but no persistent session
        devicesFlow.value = listOf(device(connected = true, requiresPersistentSession = false))
        verify(exactly = 0) { context.startServiceCompat(any()) }

        // Second emission: same connection state, now needs persistent session (e.g. user toggled keepAwake)
        devicesFlow.value = listOf(device(connected = true, requiresPersistentSession = true))

        verify(atLeast = 1) { context.startServiceCompat(fakeIntent) }
    }
}
