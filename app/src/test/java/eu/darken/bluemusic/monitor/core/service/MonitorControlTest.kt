package eu.darken.bluemusic.monitor.core.service

import android.content.Context
import android.content.Intent
import eu.darken.bluemusic.common.startServiceCompat
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.monitor.ui.MonitorNotifications
import io.mockk.Runs
import io.mockk.coEvery
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
    private lateinit var devicesSettings: DevicesSettings
    private lateinit var monitorNotifications: MonitorNotifications
    private lateinit var devicesFlow: MutableStateFlow<List<ManagedDevice>>
    private lateinit var enabledFlow: MutableStateFlow<DevicesSettings.EnabledState>
    private lateinit var fakeIntent: Intent

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        monitorNotifications = mockk(relaxed = true)
        devicesFlow = MutableStateFlow(emptyList())
        deviceRepo = mockk(relaxed = true) {
            every { devices } returns devicesFlow
        }
        enabledFlow = MutableStateFlow(DevicesSettings.EnabledState(isEnabled = true, toggleEpoch = 0L))
        devicesSettings = mockk {
            every { enabledState } returns enabledFlow
            coEvery { currentEnabledState() } answers { enabledFlow.value }
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
        active: Boolean,
        requiresPersistentSession: Boolean,
    ): ManagedDevice = mockk(relaxed = true) {
        every { isActive } returns active
        every { this@mockk.requiresPersistentSession } returns requiresPersistentSession
    }

    private fun runTestWithControl(testBody: suspend kotlinx.coroutines.test.TestScope.() -> Unit) =
        runTest(UnconfinedTestDispatcher()) {
            MonitorControl(
                appScope = backgroundScope,
                context = context,
                deviceRepo = deviceRepo,
                devicesSettings = devicesSettings,
                monitorNotifications = monitorNotifications,
            )
            testBody()
        }

    @Test
    fun `active device with requiresPersistentSession triggers service start`() = runTestWithControl {
        devicesFlow.value = listOf(device(active = true, requiresPersistentSession = true))

        verify(atLeast = 1) { context.startServiceCompat(fakeIntent) }
    }

    @Test
    fun `inactive device does NOT trigger service start`() = runTestWithControl {
        devicesFlow.value = listOf(device(active = false, requiresPersistentSession = true))

        verify(exactly = 0) { context.startServiceCompat(any()) }
    }

    @Test
    fun `active device without requiresPersistentSession does NOT trigger service start`() = runTestWithControl {
        devicesFlow.value = listOf(device(active = true, requiresPersistentSession = false))

        verify(exactly = 0) { context.startServiceCompat(any()) }
    }

    @Test
    fun `flipping requiresPersistentSession true on active device triggers service start`() = runTestWithControl {
        // First emission: active but no persistent session
        devicesFlow.value = listOf(device(active = true, requiresPersistentSession = false))
        verify(exactly = 0) { context.startServiceCompat(any()) }

        // Second emission: same connection state, now needs persistent session (e.g. user toggled keepAwake)
        devicesFlow.value = listOf(device(active = true, requiresPersistentSession = true))

        verify(atLeast = 1) { context.startServiceCompat(fakeIntent) }
    }

    @Test
    fun `persistent device while app disabled does NOT trigger service start`() = runTestWithControl {
        enabledFlow.value = DevicesSettings.EnabledState(isEnabled = false, toggleEpoch = 1L)

        devicesFlow.value = listOf(device(active = true, requiresPersistentSession = true))

        verify(exactly = 0) { context.startServiceCompat(any()) }
    }

    @Test
    fun `disabling the app stops the service`() = runTestWithControl {
        devicesFlow.value = listOf(device(active = true, requiresPersistentSession = true))
        verify(atLeast = 1) { context.startServiceCompat(fakeIntent) }

        enabledFlow.value = DevicesSettings.EnabledState(isEnabled = false, toggleEpoch = 1L)

        verify(atLeast = 1) { context.stopService(fakeIntent) }
    }

    @Test
    fun `re-enabling with persistent device connected restarts the service`() = runTestWithControl {
        enabledFlow.value = DevicesSettings.EnabledState(isEnabled = false, toggleEpoch = 1L)
        devicesFlow.value = listOf(device(active = true, requiresPersistentSession = true))
        verify(exactly = 0) { context.startServiceCompat(any()) }

        enabledFlow.value = DevicesSettings.EnabledState(isEnabled = true, toggleEpoch = 2L)

        verify(atLeast = 1) { context.startServiceCompat(fakeIntent) }
        // Re-enable must be a replacement start: a plain start in the teardown window of the
        // previous session would be ignored by onStartCommand and killed by its stopSelf.
        verify(atLeast = 1) { MonitorService.intent(any(), true) }
    }

    @Test
    fun `startMonitor is a no-op while app is disabled`() = runTest(UnconfinedTestDispatcher()) {
        enabledFlow.value = DevicesSettings.EnabledState(isEnabled = false, toggleEpoch = 1L)
        val control = MonitorControl(
            appScope = backgroundScope,
            context = context,
            deviceRepo = deviceRepo,
            devicesSettings = devicesSettings,
            monitorNotifications = monitorNotifications,
        )

        control.startMonitor(forceStart = true)

        verify(exactly = 0) { context.startServiceCompat(any()) }
    }
}
