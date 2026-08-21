package eu.darken.bluemusic.monitor.core

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.content.Context
import android.os.Process
import eu.darken.bluemusic.common.permissions.PermissionHelper
import eu.darken.bluemusic.monitor.ui.BlockedActionNotifications
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class BackgroundActivityGuardTest : BaseTest() {

    private lateinit var context: Context
    private lateinit var activityManager: ActivityManager
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var notifications: BlockedActionNotifications

    @BeforeEach
    fun setup() {
        mockkStatic(Process::class)
        every { Process.myPid() } returns OUR_PID

        activityManager = mockk(relaxed = true)
        every { activityManager.runningAppProcesses } returns emptyList()

        context = mockk(relaxed = true)
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager

        permissionHelper = mockk(relaxed = true)
        notifications = mockk(relaxed = true)
    }

    private fun createGuard() = BackgroundActivityGuard(context, permissionHelper, notifications)

    private fun setOwnImportance(importance: Int) {
        every { activityManager.runningAppProcesses } returns listOf(
            RunningAppProcessInfo().apply {
                pid = OUR_PID
                this.importance = importance
            }
        )
    }

    @Test
    fun `blocks and notifies when the overlay permission is missing`() {
        every { permissionHelper.needsOverlayPermission() } returns true

        createGuard().canStartActivityOrNotify("app launch") shouldBe false

        verify { notifications.showOverlayPermissionMissing() }
        verify(exactly = 0) { notifications.clearOverlayPermissionMissing() }
    }

    @Test
    fun `allows and clears the notification when the permission is granted`() {
        every { permissionHelper.needsOverlayPermission() } returns false

        createGuard().canStartActivityOrNotify("app launch") shouldBe true

        verify { notifications.clearOverlayPermissionMissing() }
        verify(exactly = 0) { notifications.showOverlayPermissionMissing() }
    }

    @Test
    fun `a visible activity is its own exemption, so no permission and no false alarm`() {
        every { permissionHelper.needsOverlayPermission() } returns true
        setOwnImportance(RunningAppProcessInfo.IMPORTANCE_FOREGROUND)

        createGuard().canStartActivityOrNotify("app launch") shouldBe true

        verify(exactly = 0) { notifications.showOverlayPermissionMissing() }
    }

    @Test
    fun `a foreground service alone is not an exemption`() {
        every { permissionHelper.needsOverlayPermission() } returns true
        setOwnImportance(RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE)

        createGuard().canStartActivityOrNotify("app launch") shouldBe false

        verify { notifications.showOverlayPermissionMissing() }
    }

    @Test
    fun `every blocked attempt notifies, deduplication is the notification layer's job`() {
        every { permissionHelper.needsOverlayPermission() } returns true
        val guard = createGuard()

        repeat(3) { guard.canStartActivityOrNotify("app launch") shouldBe false }

        verify(exactly = 3) { notifications.showOverlayPermissionMissing() }
    }

    @Test
    fun `syncNotificationState only clears once the permission is actually granted`() {
        every { permissionHelper.needsOverlayPermission() } returns true
        createGuard().syncNotificationState()
        verify(exactly = 0) { notifications.clearOverlayPermissionMissing() }

        every { permissionHelper.needsOverlayPermission() } returns false
        createGuard().syncNotificationState()
        verify { notifications.clearOverlayPermissionMissing() }
    }

    companion object {
        private const val OUR_PID = 1234
    }
}
