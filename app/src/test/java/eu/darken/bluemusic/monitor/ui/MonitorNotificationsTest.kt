package eu.darken.bluemusic.monitor.ui

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class MonitorNotificationsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `early notification is user presentable`() {
        val notification = MonitorNotifications.createEarlyNotification(context)

        notification.extras.getString(Notification.EXTRA_TITLE)!!.shouldNotBeEmpty()
        notification.extras.getString(Notification.EXTRA_TEXT)!!.shouldNotBeEmpty()
        notification.smallIcon.shouldNotBeNull()
        notification.contentIntent.shouldNotBeNull()
    }

    @Test
    fun `early notification ensures the status channel exists`() {
        MonitorNotifications.createEarlyNotification(context)

        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = nm.getNotificationChannel("notification.channel.core")

        channel.shouldNotBeNull()
        channel.importance shouldBe NotificationManager.IMPORTANCE_MIN
    }
}
