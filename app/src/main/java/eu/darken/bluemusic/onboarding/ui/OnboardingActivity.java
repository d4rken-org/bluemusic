package eu.darken.bluemusic.onboarding.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;

import javax.inject.Inject;

import butterknife.ButterKnife;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.onboarding.ui.intro.IntroFragment;
import eu.darken.ommvplib.base.OMMVPLib;
import eu.darken.ommvplib.injection.ComponentSource;
import eu.darken.ommvplib.injection.InjectedPresenter;
import eu.darken.ommvplib.injection.ManualInjector;
import eu.darken.ommvplib.injection.PresenterInjectionCallback;
import eu.darken.ommvplib.injection.fragment.support.HasManualSupportFragmentInjector;


public class OnboardingActivity extends AppCompatActivity implements OnboardingActivityPresenter.View, HasManualSupportFragmentInjector {

    @Inject ComponentSource<Fragment> componentSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.BaseAppTheme);
        super.onCreate(savedInstanceState);
        OMMVPLib.<OnboardingActivityPresenter.View, OnboardingActivityPresenter>builder()
                .presenterCallback(new PresenterInjectionCallback<>(this))
                .presenterSource(new InjectedPresenter<>(this))
                .attach(this);

        setContentView(R.layout.activity_layout_bluetooth);
        ButterKnife.bind(this);

        Fragment introFragment = getSupportFragmentManager().findFragmentById(R.id.frame_content);
        if (introFragment == null) introFragment = IntroFragment.newInstance();
        getSupportFragmentManager().beginTransaction().replace(R.id.frame_content, introFragment).commit();
    }

    @Override
    public ManualInjector<Fragment> supportFragmentInjector() {
        return componentSource;
    }
}