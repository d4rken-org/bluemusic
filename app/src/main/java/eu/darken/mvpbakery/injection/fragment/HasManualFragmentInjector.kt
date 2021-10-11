package eu.darken.mvpbakery.injection.fragment

import androidx.fragment.app.Fragment
import dagger.android.support.HasSupportFragmentInjector
import eu.darken.mvpbakery.injection.ManualInjector

interface HasManualFragmentInjector : HasSupportFragmentInjector {
    override fun supportFragmentInjector(): ManualInjector<Fragment>?
}
