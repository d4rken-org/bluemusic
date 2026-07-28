package eu.darken.bluemusic.monitor.core.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import eu.darken.bluemusic.R
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.monitor.ui.MonitorNotifications
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class MonitorServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * `create()` runs the real Hilt injection and the initial foreground promotion, internal
     * state that only the running monitor produces is set directly afterwards.
     */
    private fun createService(): MonitorService = Robolectric.buildService(MonitorService::class.java)
        .create()
        .get()

    private fun MonitorService.setField(name: String, value: Any?) {
        MonitorService::class.java.getDeclaredField(name)
            .apply { isAccessible = true }
            .set(this, value)
    }

    private fun MonitorService.updateNotification(devices: List<ManagedDevice>) = runBlocking {
        MonitorService::class.declaredMemberFunctions
            .single { it.name == "updateNotification" }
            .apply { isAccessible = true }
            .callSuspend(this@updateNotification, devices)
    }

    private fun notification(title: String): Notification = NotificationCompat
        .Builder(context, "notification.channel.core")
        .setSmallIcon(R.drawable.ic_notification_small)
        .setContentTitle(title)
        .build()

    @Test
    fun `repeated non-force starts promote to foreground every time`() {
        val service = createService()
        service.setField("injectionComplete", true)
        service.setField("monitoringJob", Job())

        val first = notification("first")
        service.setField("lastNotification", first)
        service.onStartCommand(MonitorService.intent(context), 0, 1) shouldBe Service.START_STICKY
        shadowOf(service).lastForegroundNotification shouldBe first

        val second = notification("second")
        service.setField("lastNotification", second)
        service.onStartCommand(MonitorService.intent(context), 0, 2) shouldBe Service.START_STICKY
        shadowOf(service).lastForegroundNotification shouldBe second
        shadowOf(service).lastForegroundNotificationId shouldBe MonitorNotifications.NOTIFICATION_ID
    }

    @Test
    fun `incomplete injection promotes with the early notification before stopping`() {
        val service = createService()
        service.setField("injectionComplete", false)
        service.setField("lastNotification", null)
        val fromOnCreate = shadowOf(service).lastForegroundNotification.shouldNotBeNull()

        service.onStartCommand(MonitorService.intent(context), 0, 1) shouldBe Service.START_NOT_STICKY

        val promoted = shadowOf(service).lastForegroundNotification.shouldNotBeNull()
        (promoted === fromOnCreate) shouldBe false
        promoted.extras.getString(Notification.EXTRA_TEXT) shouldBe
                context.getString(R.string.monitor_notification_starting)
        shadowOf(service).lastForegroundNotificationId shouldBe MonitorNotifications.NOTIFICATION_ID
        shadowOf(service).isStoppedBySelf shouldBe true
    }

    @Test
    fun `re-promotion uses the notification cached by the last update`() {
        val service = createService()
        service.setField("injectionComplete", true)
        service.setField("monitoringJob", Job())

        val current = notification("connected devices")
        service.setField("notifications", mockk<MonitorNotifications> {
            coEvery { getDevicesNotification(any()) } returns current
        })
        service.setField("notificationManager", mockk<NotificationManager>(relaxed = true))

        service.updateNotification(emptyList())
        service.onStartCommand(MonitorService.intent(context), 0, 1) shouldBe Service.START_STICKY

        shadowOf(service).lastForegroundNotification shouldBe current
    }
}
