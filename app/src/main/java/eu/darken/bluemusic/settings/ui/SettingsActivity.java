package eu.darken.bluemusic.settings.ui;

import android.content.Intent;
import android.os.Bundle;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import butterknife.ButterKnife;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.settings.ui.general.SettingsFragment;
import eu.darken.bluemusic.util.ActivityUtil;
import eu.darken.mvpbakery.base.MVPBakery;
import eu.darken.mvpbakery.base.ViewModelRetainer;
import eu.darken.mvpbakery.injection.ComponentSource;
import eu.darken.mvpbakery.injection.InjectedPresenter;
import eu.darken.mvpbakery.injection.ManualInjector;
import eu.darken.mvpbakery.injection.PresenterInjectionCallback;
import eu.darken.mvpbakery.injection.fragment.HasManualFragmentInjector;


public class SettingsActivity extends AppCompatActivity implements SettingsActivityPresenter.View, HasManualFragmentInjector {

    @Inject ComponentSource<Fragment> componentSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.BaseAppTheme);
        super.onCreate(savedInstanceState);
        MVPBakery.<SettingsActivityPresenter.View, SettingsActivityPresenter>builder()
                .presenterFactory(new InjectedPresenter<>(this))
                .presenterRetainer(new ViewModelRetainer<>(this))
                .addPresenterCallback(new PresenterInjectionCallback<>(this))
                .attach(this);

        setContentView(R.layout.activity_layout_settings);
        ButterKnife.bind(this);
    }

    @Override
    public void showSettings() {
        Fragment introFragment = getSupportFragmentManager().findFragmentById(R.id.frame_content);
        if (introFragment == null) introFragment = SettingsFragment.newInstance();
        getSupportFragmentManager().beginTransaction().replace(R.id.frame_content, introFragment).commitAllowingStateLoss();
    }

    @Override
    public ManualInjector<Fragment> supportFragmentInjector() {
        return componentSource;
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode, @Nullable Bundle options) {
        ActivityUtil.tryStartActivity(this, () -> SettingsActivity.super.startActivityForResult(intent, requestCode, options));
    }
}