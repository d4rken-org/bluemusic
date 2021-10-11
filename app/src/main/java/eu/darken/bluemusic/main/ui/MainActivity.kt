package eu.darken.bluemusic.main.ui;

import android.content.Intent;
import android.os.Bundle;

import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import butterknife.ButterKnife;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.main.ui.managed.ManagedDevicesFragment;
import eu.darken.bluemusic.onboarding.ui.OnboardingActivity;
import eu.darken.bluemusic.util.iap.IAPRepo;
import eu.darken.mvpbakery.base.MVPBakery;
import eu.darken.mvpbakery.base.ViewModelRetainer;
import eu.darken.mvpbakery.injection.ComponentSource;
import eu.darken.mvpbakery.injection.InjectedPresenter;
import eu.darken.mvpbakery.injection.ManualInjector;
import eu.darken.mvpbakery.injection.PresenterInjectionCallback;
import eu.darken.mvpbakery.injection.fragment.HasManualFragmentInjector;


public class MainActivity extends AppCompatActivity implements MainActivityPresenter.View, HasManualFragmentInjector {

    @Inject ComponentSource<Fragment> componentSource;
    @Inject IAPRepo iapRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.BaseAppTheme);
        super.onCreate(savedInstanceState);
        MVPBakery.<MainActivityPresenter.View, MainActivityPresenter>builder()
                .presenterFactory(new InjectedPresenter<>(this))
                .presenterRetainer(new ViewModelRetainer<>(this))
                .addPresenterCallback(new PresenterInjectionCallback<>(this))
                .attach(this);
        setContentView(R.layout.activity_layout_main);
        ButterKnife.bind(this);
    }

    @NotNull
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

    private boolean shouldReCheck = false;

    @Override
    protected void onStart() {
        super.onStart();
        if (shouldReCheck) iapRepo.recheck();
    }

    @Override
    protected void onStop() {
        shouldReCheck = true;
        super.onStop();
    }
}