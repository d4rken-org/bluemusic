package eu.darken.bluemusic.main.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;

import javax.inject.Inject;

import butterknife.ButterKnife;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.main.ui.managed.ManagedDevicesFragment;
import eu.darken.bluemusic.onboarding.ui.OnboardingActivity;
import eu.darken.ommvplib.base.OMMVPLib;
import eu.darken.ommvplib.injection.ComponentSource;
import eu.darken.ommvplib.injection.InjectedPresenter;
import eu.darken.ommvplib.injection.ManualInjector;
import eu.darken.ommvplib.injection.PresenterInjectionCallback;
import eu.darken.ommvplib.injection.fragment.support.HasManualSupportFragmentInjector;


public class MainActivity extends AppCompatActivity implements MainActivityPresenter.View, HasManualSupportFragmentInjector {

    @Inject ComponentSource<Fragment> componentSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.BaseAppTheme);
        super.onCreate(savedInstanceState);
        OMMVPLib.<MainActivityPresenter.View, MainActivityPresenter>builder()
                .presenterCallback(new PresenterInjectionCallback<>(this))
                .presenterSource(new InjectedPresenter<>(this))
                .attach(this);
        setContentView(R.layout.activity_layout_main);
        ButterKnife.bind(this);
    }

    @Override
    public ManualInjector<Fragment> supportFragmentInjector() {
        return componentSource;
    }

    @Override
    public void showOnboarding() {
        startActivity(new Intent(this, OnboardingActivity.class));
    }

    @Override
    public void showDevices() {
        Fragment introFragment = getSupportFragmentManager().findFragmentById(R.id.frame_content);
        if (introFragment == null) introFragment = ManagedDevicesFragment.newInstance();
        getSupportFragmentManager().beginTransaction().replace(R.id.frame_content, introFragment).commitAllowingStateLoss();
    }
}