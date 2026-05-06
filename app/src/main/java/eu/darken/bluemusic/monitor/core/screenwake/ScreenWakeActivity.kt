package eu.darken.bluemusic.monitor.core.screenwake

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
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
        // Hold the screen on long enough for the user to interact with whatever
        // launched (typically the music app) before system display timeout fires.
        // FLAG_KEEP_SCREEN_ON is dropped automatically when the activity finishes.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        // setTurnScreenOn only fires when the activity is resumed/visible, so post the
        // delayed finish from onResume rather than onCreate. The hold runs until the
        // timer fires, the user touches the screen (see dispatchTouchEvent), or the OS
        // kills the activity under memory pressure — all three drop FLAG_KEEP_SCREEN_ON
        // and let the system display timeout take over.
        Handler(Looper.getMainLooper()).postDelayed(::finish, HOLD_DURATION_MS)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // The user is interacting now — get out of their way immediately. The host app
        // they tapped will keep the screen on naturally from here.
        finish()
        return true
    }

    companion object {
        // Long enough to read the screen and tap a launched music app; short enough
        // not to drain battery on a forgotten connection. Single tunable constant.
        private const val HOLD_DURATION_MS = 60_000L
    }
}
