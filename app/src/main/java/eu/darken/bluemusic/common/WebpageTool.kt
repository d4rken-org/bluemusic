package eu.darken.bluemusic.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.log
import javax.inject.Inject

@Reusable
class WebpageTool @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    // Returns whether an activity was actually started, so callers that gate behaviour on the page
    // having opened (e.g. the FOSS sponsor unlock heuristic) don't fire when no browser handled it.
    fun open(address: String): Boolean = open(context, address)

    companion object {
        fun open(context: Context, address: String): Boolean {
            val intent = Intent(Intent.ACTION_VIEW, address.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(intent)
                true
            } catch (e: ActivityNotFoundException) {
                log(ERROR) { "Failed to launch. No compatible activity!" }
                false
            } catch (e: SecurityException) {
                // Permission Denial: starting Intent { act=android.intent.action.VIEW dat=https://github.com/...
                // flg=0x10000000 cmp=com.mxtech.videoplayer.pro/com.mxtech.videoplayer.ActivityWebBrowser }
                log(ERROR) { "Failed to launch activity due to $e" }
                false
            }
        }
    }
}