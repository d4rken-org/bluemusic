package eu.darken.bluemusic.main.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;

import javax.inject.Inject;

import butterknife.ButterKnife;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.main.ui.managed.ManagedDevicesFragment;
import eu.darken.bluemusic.onboarding.ui.OnboardingActivity;
import eu.darken.ommvplib.injection.ComponentPresenterActivity;
import eu.darken.ommvplib.injection.ComponentSource;
import eu.darken.ommvplib.injection.ManualInjector;
import eu.darken.ommvplib.injection.fragment.support.HasManualSupportFragmentInjector;


public class MainActivity extends ComponentPresenterActivity<MainActivityPresenter.View, MainActivityPresenter, MainActivityComponent>
        implements MainActivityPresenter.View, HasManualSupportFragmentInjector {

    @Inject ComponentSource<Fragment> componentSource;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.BaseAppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_layout_main);
        ButterKnife.bind(this);
    }

    @Override
    public Class<? extends MainActivityPresenter> getTypeClazz() {
        return MainActivityPresenter.class;
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
        getSupportFragmentManager().beginTransaction().replace(R.id.frame_content, introFragment).commit();
    }
}