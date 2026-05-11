package eu.darken.bluemusic.monitor.core.screenwake

import io.kotest.matchers.collections.shouldNotContain
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ScreenWakeActivityTest : BaseTest() {

    @Test
    fun `does not intercept touch events`() {
        val methodNames = ScreenWakeActivity::class.java.declaredMethods.map { it.name }

        methodNames shouldNotContain "dispatchTouchEvent"
    }
}
