package eu.darken.bluemusic.monitor.core.audio

import android.app.NotificationManager
import eu.darken.bluemusic.common.BuildWrap
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DndToolTest : BaseTest() {

    private lateinit var notificationManager: NotificationManager

    @BeforeEach
    fun setup() {
        notificationManager = mockk(relaxed = true)
        every { notificationManager.isNotificationPolicyAccessGranted } returns true
        // DND is currently off (all interruptions allowed).
        every { notificationManager.currentInterruptionFilter } returns NotificationManager.INTERRUPTION_FILTER_ALL
        mockkObject(BuildWrap.VERSION)
        every { BuildWrap.VERSION.CODENAME } returns "REL"
    }

    @AfterEach
    fun teardown() {
        unmockkObject(BuildWrap.VERSION)
    }

    private fun tool() = DndTool(notificationManager)

    @Test
    fun `setDndMode OFF on API 35+ is a no-op`() {
        every { BuildWrap.VERSION.SDK_INT } returns 35
        // Pretend DND is currently on so only the OFF-guard could suppress the call.
        every { notificationManager.currentInterruptionFilter } returns NotificationManager.INTERRUPTION_FILTER_PRIORITY

        tool().setDndMode(DndMode.OFF) shouldBe false
        verify(exactly = 0) { notificationManager.setInterruptionFilter(any()) }
    }

    @Test
    fun `setDndMode OFF below API 35 turns DND off`() {
        every { BuildWrap.VERSION.SDK_INT } returns 30
        every { notificationManager.currentInterruptionFilter } returns NotificationManager.INTERRUPTION_FILTER_PRIORITY

        tool().setDndMode(DndMode.OFF) shouldBe true
        verify(exactly = 1) { notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
    }

    @Test
    fun `setDndMode PRIORITY on API 35+ still applies`() {
        every { BuildWrap.VERSION.SDK_INT } returns 35

        tool().setDndMode(DndMode.PRIORITY_ONLY) shouldBe true
        verify(exactly = 1) { notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY) }
    }

    @Test
    fun `setDndMode skips when already in desired mode`() {
        every { BuildWrap.VERSION.SDK_INT } returns 35
        every { notificationManager.currentInterruptionFilter } returns NotificationManager.INTERRUPTION_FILTER_PRIORITY

        tool().setDndMode(DndMode.PRIORITY_ONLY) shouldBe false
        verify(exactly = 0) { notificationManager.setInterruptionFilter(any()) }
    }
}
