package eu.darken.bluemusic.settings.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import eu.darken.bluemusic.R
import eu.darken.bluemusic.settings.ui.general.SettingsFragment
import eu.darken.bluemusic.util.ActivityUtil
import eu.darken.mvpbakery.base.MVPBakery.Companion.builder
import eu.darken.mvpbakery.base.ViewModelRetainer
import eu.darken.mvpbakery.injection.ComponentSource
import eu.darken.mvpbakery.injection.InjectedPresenter
import eu.darken.mvpbakery.injection.ManualInjector
import eu.darken.mvpbakery.injection.PresenterInjectionCallback
import eu.darken.mvpbakery.injection.fragment.HasManualFragmentInjector
import javax.inject.Inject

class SettingsActivity : AppCompatActivity(), SettingsActivityPresenter.View, HasManualFragmentInjector {
    @Inject lateinit var componentSource: ComponentSource<Fragment>
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.BaseAppTheme)
        super.onCreate(savedInstanceState)
        builder<SettingsActivityPresenter.View, SettingsActivityPresenter>()
                .presenterFactory(InjectedPresenter(this))
                .presenterRetainer(ViewModelRetainer(this))
                .addPresenterCallback(PresenterInjectionCallback(this))
                .attach(this)
        setContentView(R.layout.activity_layout_settings)
    }

    override fun showSettings() {
        var introFragment = supportFragmentManager.findFragmentById(R.id.frame_content)
        if (introFragment == null) introFragment = SettingsFragment.newInstance()
        supportFragmentManager.beginTransaction().replace(R.id.frame_content, introFragment!!).commitAllowingStateLoss()
    }

    override fun supportFragmentInjector(): ManualInjector<Fragment> = componentSource

    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        ActivityUtil.tryStartActivity(this) { super@SettingsActivity.startActivityForResult(intent, requestCode, options) }
    }
}