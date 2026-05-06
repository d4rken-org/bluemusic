package eu.darken.bluemusic.monitor.core.screenwake

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import eu.darken.bluemusic.common.ui.Activity2

class ScreenWakeActivity : Activity2() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Defer finish until the activity has actually become resumed/visible —
        // setTurnScreenOn only fires when the activity goes to the foreground.
        Handler(Looper.getMainLooper()).post { finish() }
    }
}
