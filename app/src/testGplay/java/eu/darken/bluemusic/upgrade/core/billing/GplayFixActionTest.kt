package eu.darken.bluemusic.upgrade.core.billing

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import eu.darken.bluemusic.R
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import testhelpers.BaseTest
import testhelpers.TestApplication

/**
 * The error dialog's "Google Play" button on devices where the launch never lands: a refused or
 * unresolvable intent has to end in a toast, not a crash. The intent shape itself is pinned by
 * [eu.darken.bluemusic.common.error.ComposeErrorDialogTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class GplayFixActionTest : BaseTest() {

    /** Play is installed but unreachable: disabled app, restricted profile or a guarding ROM. */
    class DeniedLaunchActivity : Activity() {
        override fun startActivity(intent: Intent): Unit = throw SecurityException("Permission Denial")
    }

    /** No app settings screen resolves the intent at all, e.g. a stripped-down ROM. */
    class UnresolvedLaunchActivity : Activity() {
        override fun startActivity(intent: Intent): Unit = throw ActivityNotFoundException("No Activity found")
    }

    private val fixAction: (Activity) -> Unit
        get() = GplayServiceUnavailableException(RuntimeException("Play hiccup"))
            .getLocalizedError().fixAction.shouldNotBeNull()

    private fun <T : Activity> assertNotInstalledToast(clazz: Class<T>) {
        val activity = Robolectric.buildActivity(clazz).setup().get()

        fixAction.invoke(activity)

        ShadowToast.getTextOfLatestToast() shouldBe
            activity.getString(R.string.upgrades_gplay_not_installed_message)
    }

    @Test
    fun `a denied launch shows the not-installed toast instead of crashing`() {
        assertNotInstalledToast(DeniedLaunchActivity::class.java)
    }

    @Test
    fun `an unresolvable launch shows the not-installed toast instead of crashing`() {
        assertNotInstalledToast(UnresolvedLaunchActivity::class.java)
    }
}
