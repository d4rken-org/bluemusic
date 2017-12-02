package eu.darken.bluemusic.onboarding.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;

import javax.inject.Inject;

import butterknife.ButterKnife;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.onboarding.ui.intro.IntroFragment;
import eu.darken.ommvplib.injection.ComponentPresenterActivity;
import eu.darken.ommvplib.injection.ComponentSource;
import eu.darken.ommvplib.injection.ManualInjector;
import eu.darken.ommvplib.injection.fragment.support.HasManualSupportFragmentInjector;


public class OnboardingActivity extends ComponentPresenterActivity<OnboardingActivityPresenter.View, OnboardingActivityPresenter, OnboardingActivityComponent>
        implements OnboardingActivityPresenter.View, HasManualSupportFragmentInjector {

    @Inject ComponentSource<Fragment> componentSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.BaseAppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_layout_bluetooth);
        ButterKnife.bind(this);

        Fragment introFragment = getSupportFragmentManager().findFragmentById(R.id.frame_content);
        if (introFragment == null) introFragment = IntroFragment.newInstance();
        getSupportFragmentManager().beginTransaction().replace(R.id.frame_content, introFragment).commit();
    }

    @Override
    public Class<? extends OnboardingActivityPresenter> getTypeClazz() {
        return OnboardingActivityPresenter.class;
    }

    @Override
    public ManualInjector<Fragment> supportFragmentInjector() {
        return componentSource;
    }
}