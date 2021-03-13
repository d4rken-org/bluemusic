package eu.darken.bluemusic.util

import android.content.ActivityNotFoundException
import com.bugsnag.android.Event
import com.bugsnag.android.OnErrorCallback
import com.bugsnag.android.Severity
import eu.darken.bluemusic.AppComponent
import eu.darken.bluemusic.BuildConfig
import eu.darken.bluemusic.ManualReport
import eu.darken.bluemusic.settings.core.Settings
import javax.inject.Inject

@AppComponent.Scope
class BugsnagErrorHandler @Inject constructor(
        private val settings: Settings,
        private val bugsnagTree: BugsnagTree
) : OnErrorCallback {

    override fun onError(event: Event): Boolean {
        bugsnagTree.update(event)

        event.severity = when (event.originalError) {
            is ManualReport -> Severity.INFO
            is ActivityNotFoundException -> Severity.WARNING
            else -> Severity.ERROR
        }

        return !BuildConfig.DEBUG && settings.isBugReportingEnabled
    }

}