package eu.darken.bluemusic.common.debug

import android.app.Application

interface AutomaticBugReporter {

    fun setup(application: Application)

    fun leaveBreadCrumb(crumb: String)

    fun notify(throwable: Throwable)
}