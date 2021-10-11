package eu.darken.bluemusic.onboarding.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import eu.darken.bluemusic.R
import eu.darken.bluemusic.onboarding.ui.intro.IntroFragment.Companion.newInstance
import eu.darken.mvpbakery.base.MVPBakery.Companion.builder
import eu.darken.mvpbakery.base.ViewModelRetainer
import eu.darken.mvpbakery.injection.ComponentSource
import eu.darken.mvpbakery.injection.InjectedPresenter
import eu.darken.mvpbakery.injection.ManualInjector
import eu.darken.mvpbakery.injection.PresenterInjectionCallback
import eu.darken.mvpbakery.injection.fragment.HasManualFragmentInjector
import javax.inject.Inject

class OnboardingActivity : AppCompatActivity(), OnboardingActivityPresenter.View, HasManualFragmentInjector {

    @Inject lateinit var componentSource: ComponentSource<Fragment>

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.BaseAppTheme)
        super.onCreate(savedInstanceState)
        builder<OnboardingActivityPresenter.View, OnboardingActivityPresenter>()
                .presenterFactory(InjectedPresenter(this))
                .presenterRetainer(ViewModelRetainer(this))
                .addPresenterCallback(PresenterInjectionCallback(this))
                .attach(this)
        setContentView(R.layout.activity_layout_bluetooth)
    }

    override fun showIntro() {
        var introFragment = supportFragmentManager.findFragmentById(R.id.frame_content)
        if (introFragment == null) introFragment = newInstance()
        supportFragmentManager.beginTransaction().replace(R.id.frame_content, introFragment).commitAllowingStateLoss()
    }

    override fun supportFragmentInjector(): ManualInjector<Fragment>? {
        return componentSource
    }
}