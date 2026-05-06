package eu.darken.bluemusic.monitor.core

import io.kotest.matchers.collections.shouldNotContain
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * Reflection-only regression guards for [WakeLockManager].
 *
 * Pure JVM, no Android / Robolectric — the screenWakeLock field was the
 * deprecated SCREEN_BRIGHT_WAKE_LOCK that no longer wakes the display
 * since API 28. This test enforces it stays gone.
 */
class WakeLockManagerFieldsTest : BaseTest() {

    @Test
    fun `screenWakeLock field is removed`() {
        val fieldNames = WakeLockManager::class.java.declaredFields.map { it.name }
        fieldNames shouldNotContain "screenWakeLock"
    }
}
