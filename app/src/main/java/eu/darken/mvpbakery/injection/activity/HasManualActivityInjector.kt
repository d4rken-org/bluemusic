package eu.darken.mvpbakery.injection.activity

import android.app.Activity

import dagger.android.HasActivityInjector
import eu.darken.mvpbakery.injection.ManualInjector

interface HasManualActivityInjector : HasActivityInjector {

    override fun activityInjector(): ManualInjector<Activity>
}
