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
    private val testSourceDevice = SourceDeviceWrapper(
        address = testAddress,
        alias = "TestDevice",
        name = "TestDevice",
        deviceType = SourceDevice.Type.HEADPHONES,
        isConnected = true,
    )

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

    private fun device(dndMode: DndMode? = DndMode.PRIORITY_ONLY): ManagedDevice = ManagedDevice(
        isConnected = true,
        device = testSourceDevice,
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
    fun `handle sets DND mode when applicable`() = runTest(UnconfinedTestDispatcher()) {
        every { permissionHelper.hasNotificationPolicyAccess() } returns true
        module().handle(DeviceEvent.Connected(device(dndMode = DndMode.PRIORITY_ONLY)))

        coVerify(exactly = 1) { dndTool.setDndMode(DndMode.PRIORITY_ONLY) }
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
