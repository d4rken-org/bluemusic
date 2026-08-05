package eu.darken.bluemusic.upgrade.core.billing

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.ca.toCaString
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.*
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.error.HasLocalizedError
import eu.darken.bluemusic.common.error.LocalizedError

class GplayServiceUnavailableException(cause: Throwable) :
    BillingException("Google Play services are unavailable.", cause), HasLocalizedError {

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.upgrades_gplay_unavailable_error_title.toCaString(),
        description = R.string.upgrades_gplay_unavailable_error_description.toCaString(),
        fixActionLabel = "Google Play".toCaString(),
        fixAction = { activity ->
            val intent = Intent().apply {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.fromParts("package", GPLAY_PKG, null)
            }

            try {
                activity.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                onLaunchFailed(activity, e)
            } catch (e: SecurityException) {
                // Play can be installed but unreachable: disabled app, work/restricted profile or a
                // ROM that guards the settings screen. The launch is denied, not unresolvable.
                onLaunchFailed(activity, e)
            }
        }
    )

    private fun onLaunchFailed(activity: Activity, e: Exception) {
        log(ERROR) { "Can't launch settings intent for Google Play: $e" }
        Toast.makeText(activity, R.string.upgrades_gplay_not_installed_message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val GPLAY_PKG = "com.android.vending"
    }
}