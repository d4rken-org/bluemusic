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
    fun `setWakeLock true acquires CPU lock and is idempotent`() = runTest {
        // Pre-condition: no wake lock exists before setWakeLock is called.
        ShadowPowerManager.getLatestWakeLock() shouldBe null

        val mgr = manager()
        mgr.setWakeLock(true)

        // Regression guard: tag and held state. The latest lock IS the only lock since
        // none existed pre-call.
        val lock = ShadowPowerManager.getLatestWakeLock()
        lock shouldNotBe null
        Shadows.shadowOf(lock).tag shouldBe "BlueMusic:KeepAwakeCPU"
        lock.isHeld shouldBe true

        // Idempotency: calling again must not create a second lock.
        mgr.setWakeLock(true)
        ShadowPowerManager.getLatestWakeLock() shouldBe lock
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
    fun `wakeScreenNow noop on API29+ without overlay perm`() {
        every { permissionHelper.needsOverlayPermission() } returns true

        val mgr = manager()
        mgr.wakeScreenNow()

        val nextIntent = Shadows.shadowOf(context).peekNextStartedActivity()
        nextIntent shouldBe null
    }

    @Test
    fun `wakeScreenNow launches activity when overlay ok`() {
        // needsOverlayPermission() returns false in two cases:
        //   1. Android 6-9 (BAL not enforced, no SAW required)
        //   2. Android 10+ with SAW already granted
        // In both, the activity launch must proceed.
        every { permissionHelper.needsOverlayPermission() } returns false

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

    @Test
    fun `wakeScreenNow gate uses needsOverlayPermission only`() {
        // Codex regression guard: catches a future change reverting back to the
        // overly-broad canDrawOverlays() gate which would silently no-op on Android 6-9.
        every { permissionHelper.needsOverlayPermission() } returns false
        every { permissionHelper.canDrawOverlays() } returns false // simulating Android 6-9 default

        val mgr = manager()
        mgr.wakeScreenNow()

        // Should still launch — needsOverlayPermission is the only gate.
        Shadows.shadowOf(context).peekNextStartedActivity() shouldNotBe null
    }
}
