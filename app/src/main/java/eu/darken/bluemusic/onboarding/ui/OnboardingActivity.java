package eu.darken.bluemusic.onboarding.ui;

import android.os.Bundle;

import javax.inject.Inject;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import butterknife.ButterKnife;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.onboarding.ui.intro.IntroFragment;
import eu.darken.mvpbakery.base.MVPBakery;
import eu.darken.mvpbakery.base.ViewModelRetainer;
import eu.darken.mvpbakery.injection.ComponentSource;
import eu.darken.mvpbakery.injection.InjectedPresenter;
import eu.darken.mvpbakery.injection.ManualInjector;
import eu.darken.mvpbakery.injection.PresenterInjectionCallback;
import eu.darken.mvpbakery.injection.fragment.HasManualFragmentInjector;


public class OnboardingActivity extends AppCompatActivity implements OnboardingActivityPresenter.View, HasManualFragmentInjector {

    @Inject ComponentSource<Fragment> componentSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.BaseAppTheme);
        super.onCreate(savedInstanceState);
        MVPBakery.<OnboardingActivityPresenter.View, OnboardingActivityPresenter>builder()
                .presenterFactory(new InjectedPresenter<>(this))
                .presenterRetainer(new ViewModelRetainer<>(this))
                .addPresenterCallback(new PresenterInjectionCallback<>(this))
                .attach(this);

        setContentView(R.layout.activity_layout_bluetooth);
        ButterKnife.bind(this);

    }

    @Override
    public void showIntro() {
        Fragment introFragment = getSupportFragmentManager().findFragmentById(R.id.frame_content);
        if (introFragment == null) introFragment = IntroFragment.newInstance();
        getSupportFragmentManager().beginTransaction().replace(R.id.frame_content, introFragment).commitAllowingStateLoss();
    }

    @Override
    public ManualInjector<Fragment> supportFragmentInjector() {
        return componentSource;
    }
}