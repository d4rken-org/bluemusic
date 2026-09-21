package eu.darken.bluemusic.monitor.core.modules.connection

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.SourceDeviceWrapper
import eu.darken.bluemusic.common.BuildWrap
import eu.darken.bluemusic.common.permissions.PermissionHelper
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.DndMode
import eu.darken.bluemusic.monitor.core.audio.DndTool
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

@OptIn(ExperimentalCoroutinesApi::class)
class DndModeModuleTest : BaseTest() {

    private val testAddress = "AA:BB:CC:DD:EE:FF"

    private lateinit var dndTool: DndTool
    private lateinit var permissionHelper: PermissionHelper

    @BeforeEach
    fun setup() {
        dndTool = mockk(relaxed = true)
        permissionHelper = mockk(relaxed = true)
        every { permissionHelper.hasNotificationPolicyAccess() } returns true
        // Pure JVM tests see Build.VERSION.SDK_INT=0; fake API 23+ so DndModeModule's
        // hasApiLevel(M) gate passes.
        mockkObject(BuildWrap.VERSION)
        every { BuildWrap.VERSION.SDK_INT } returns 30
    }

    @AfterEach
    fun teardown() {
        unmockkObject(BuildWrap.VERSION)
    }

    private fun device(
        dndMode: DndMode? = DndMode.PRIORITY_ONLY,
        deviceType: SourceDevice.Type = SourceDevice.Type.HEADPHONES,
    ): ManagedDevice = ManagedDevice(
        isConnected = true,
        device = SourceDeviceWrapper(
            address = testAddress,
            alias = "TestDevice",
            name = "TestDevice",
            deviceType = deviceType,
            isConnected = true,
        ),
        config = DeviceConfigEntity(
            address = testAddress,
            isEnabled = true,
            dndMode = dndMode,
        ),
    )

    private fun module() = DndModeModule(dndTool, permissionHelper)

    @Test
    fun `appliesTo Connected with dndMode configured and permission granted is true`() {
        every { permissionHelper.hasNotificationPolicyAccess() } returns true
        module().appliesTo(DeviceEvent.Connected(device())) shouldBe true
    }

    @Test
    fun `appliesTo Connected without dndMode configured is false`() {
        module().appliesTo(DeviceEvent.Connected(device(dndMode = null))) shouldBe false
    }

    @Test
    fun `appliesTo Connected with dndMode but permission revoked is false`() {
        // The whole point of including the permission in appliesTo: don't pay the
        // dispatcher's settle barrier when the module will return early anyway.
        every { permissionHelper.hasNotificationPolicyAccess() } returns false
        module().appliesTo(DeviceEvent.Connected(device())) shouldBe false
    }

    @Test
    fun `appliesTo Disconnected is false`() {
        module().appliesTo(DeviceEvent.Disconnected(device())) shouldBe false
    }

    @Test
    fun `appliesTo OFF below API 35 is true`() {
        // OFF still works via the legacy setInterruptionFilter path before Android 15.
        every { BuildWrap.VERSION.SDK_INT } returns 30
        module().appliesTo(DeviceEvent.Connected(device(dndMode = DndMode.OFF))) shouldBe true
    }

    @Test
    fun `appliesTo OFF on API 35+ is true`() {
        // An app can always deactivate its own DND contribution, on every API level.
        every { BuildWrap.VERSION.SDK_INT } returns 35
        module().appliesTo(DeviceEvent.Connected(device(dndMode = DndMode.OFF))) shouldBe true
    }

    @Test
    fun `appliesTo non-OFF mode on API 35+ is still true`() {
        every { BuildWrap.VERSION.SDK_INT } returns 35
        module().appliesTo(DeviceEvent.Connected(device(dndMode = DndMode.PRIORITY_ONLY))) shouldBe true
    }

    @Test
    fun `handle sets DND mode when applicable`() = runTest(UnconfinedTestDispatcher()) {
        every { permissionHelper.hasNotificationPolicyAccess() } returns true
        module().handle(DeviceEvent.Connected(device(dndMode = DndMode.PRIORITY_ONLY)))

        coVerify(exactly = 1) { dndTool.setDndMode(DndMode.PRIORITY_ONLY) }
    }

    @Test
    fun `handle sets OFF for the phone speaker on API 35+`() = runTest(UnconfinedTestDispatcher()) {
        every { BuildWrap.VERSION.SDK_INT } returns 35
        val speaker = device(dndMode = DndMode.OFF, deviceType = SourceDevice.Type.PHONE_SPEAKER)

        module().handle(DeviceEvent.Connected(speaker))

        coVerify(exactly = 1) { dndTool.setDndMode(DndMode.OFF) }
    }

    @Test
    fun `handle skips DND mode when not applicable`() = runTest(UnconfinedTestDispatcher()) {
        // handle() should be defensive — if dispatcher accidentally called us when
        // appliesTo would return false, we still no-op cleanly.
        every { permissionHelper.hasNotificationPolicyAccess() } returns false
        module().handle(DeviceEvent.Connected(device(dndMode = DndMode.PRIORITY_ONLY)))

        coVerify(exactly = 0) { dndTool.setDndMode(any()) }
    }
}
