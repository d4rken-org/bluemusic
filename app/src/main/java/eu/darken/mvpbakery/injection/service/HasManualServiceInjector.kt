package eu.darken.mvpbakery.injection.service


import android.app.Service

import dagger.android.HasServiceInjector
import eu.darken.mvpbakery.injection.ManualInjector

interface HasManualServiceInjector : HasServiceInjector {
    override fun serviceInjector(): ManualInjector<Service>
}
