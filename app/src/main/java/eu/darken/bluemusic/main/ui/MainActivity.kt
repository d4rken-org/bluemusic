package eu.darken.bluemusic.main.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import eu.darken.bluemusic.R
import eu.darken.bluemusic.main.ui.managed.ManagedDevicesFragment.Companion.newInstance
import eu.darken.bluemusic.onboarding.ui.OnboardingActivity
import eu.darken.bluemusic.util.iap.IAPRepo
import eu.darken.mvpbakery.base.MVPBakery.Companion.builder
import eu.darken.mvpbakery.base.ViewModelRetainer
import eu.darken.mvpbakery.injection.ComponentSource
import eu.darken.mvpbakery.injection.InjectedPresenter
import eu.darken.mvpbakery.injection.ManualInjector
import eu.darken.mvpbakery.injection.PresenterInjectionCallback
import eu.darken.mvpbakery.injection.fragment.HasManualFragmentInjector
import javax.inject.Inject

class MainActivity : AppCompatActivity(), MainActivityPresenter.View, HasManualFragmentInjector {
    @Inject lateinit var componentSource: ComponentSource<Fragment>
    @Inject lateinit var iapRepo: IAPRepo

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.BaseAppTheme)
        super.onCreate(savedInstanceState)
        builder<MainActivityPresenter.View, MainActivityPresenter>()
                .presenterFactory(InjectedPresenter(this))
                .presenterRetainer(ViewModelRetainer(this))
                .addPresenterCallback(PresenterInjectionCallback(this))
                .attach(this)
        setContentView(R.layout.activity_layout_main)
    }

    override fun supportFragmentInjector(): ManualInjector<Fragment> = componentSource

    override fun showOnboarding() {
        startActivity(Intent(this, OnboardingActivity::class.java))
    }

    override fun showDevices() {
        var introFragment = supportFragmentManager.findFragmentById(R.id.frame_content)
        if (introFragment == null) introFragment = newInstance()
        supportFragmentManager.beginTransaction().replace(R.id.frame_content, introFragment).commitAllowingStateLoss()
    }

    private var shouldReCheck = false
    override fun onStart() {
        super.onStart()
        if (shouldReCheck) iapRepo.recheck()
    }

    override fun onStop() {
        shouldReCheck = true
        super.onStop()
    }
}