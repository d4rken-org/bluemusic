package eu.darken.bluemusic.monitor.core

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import eu.darken.bluemusic.common.permissions.PermissionHelper
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class WakeLockManagerTest {

    private lateinit var context: Application
    private lateinit var permissionHelper: PermissionHelper

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        permissionHelper = mockk(relaxed = true)
        ShadowPowerManager.clearWakeLocks()
    }

    @After
    fun tearDown() {
        ShadowPowerManager.clearWakeLocks()
    }

    private fun manager() = WakeLockManager(context, permissionHelper)

    @Test
    fun `setWakeLock true acquires only CPU lock`() = runTest {
        val mgr = manager()
        mgr.setWakeLock(true)

        // Robolectric tracks every PowerManager.newWakeLock() call.
        // Regression guard: only the CPU lock is created/acquired (no SCREEN_BRIGHT_WAKE_LOCK).
        val lock = ShadowPowerManager.getLatestWakeLock()
        lock shouldNotBe null
        Shadows.shadowOf(lock).tag shouldBe "BlueMusic:KeepAwakeCPU"
        lock.isHeld shouldBe true
    }

    @Test
    fun `setWakeLock false releases CPU lock`() = runTest {
        val mgr = manager()
        mgr.setWakeLock(true)
        mgr.setWakeLock(false)

        val lock = ShadowPowerManager.getLatestWakeLock()
        lock shouldNotBe null
        lock.isHeld shouldBe false
    }

    @Test
    fun `wakeScreenNow noop without overlay permission`() {
        every { permissionHelper.canDrawOverlays() } returns false

        val mgr = manager()
        mgr.wakeScreenNow()

        val nextIntent = Shadows.shadowOf(context).peekNextStartedActivity()
        nextIntent shouldBe null
    }

    @Test
    fun `wakeScreenNow launches ScreenWakeActivity when overlay granted`() {
        every { permissionHelper.canDrawOverlays() } returns true

        val mgr = manager()
        mgr.wakeScreenNow()

        val launched = Shadows.shadowOf(context).peekNextStartedActivity()
        launched shouldNotBe null
        launched!!.component?.className shouldBe
                "eu.darken.bluemusic.monitor.core.screenwake.ScreenWakeActivity"

        val flags = launched.flags
        (flags and Intent.FLAG_ACTIVITY_NEW_TASK) shouldBe Intent.FLAG_ACTIVITY_NEW_TASK
        (flags and Intent.FLAG_ACTIVITY_NO_HISTORY) shouldBe Intent.FLAG_ACTIVITY_NO_HISTORY
        (flags and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) shouldBe Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
    }
}
