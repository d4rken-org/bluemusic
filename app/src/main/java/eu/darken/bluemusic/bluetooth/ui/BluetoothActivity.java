package eu.darken.bluemusic.bluetooth.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import javax.inject.Inject;

import butterknife.ButterKnife;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.bluetooth.ui.discover.DiscoverFragment;
import eu.darken.mvpbakery.base.MVPBakery;
import eu.darken.mvpbakery.base.ViewModelRetainer;
import eu.darken.mvpbakery.injection.ComponentSource;
import eu.darken.mvpbakery.injection.InjectedPresenter;
import eu.darken.mvpbakery.injection.ManualInjector;
import eu.darken.mvpbakery.injection.PresenterInjectionCallback;
import eu.darken.mvpbakery.injection.fragment.HasManualFragmentInjector;


public class BluetoothActivity extends AppCompatActivity implements BluetoothActivityPresenter.View, HasManualFragmentInjector {

    @Inject ComponentSource<Fragment> componentSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.BaseAppTheme);
        super.onCreate(savedInstanceState);
        MVPBakery.<BluetoothActivityPresenter.View, BluetoothActivityPresenter>builder()
                .presenterFactory(new InjectedPresenter<>(this))
                .presenterRetainer(new ViewModelRetainer<>(this))
                .addPresenterCallback(new PresenterInjectionCallback<>(this))
                .attach(this);

        setContentView(R.layout.activity_layout_bluetooth);
        ButterKnife.bind(this);
    }

    @Override
    public void showDiscovery() {
        Fragment introFragment = getSupportFragmentManager().findFragmentById(R.id.frame_content);
        if (introFragment == null) introFragment = DiscoverFragment.newInstance();
        getSupportFragmentManager().beginTransaction().replace(R.id.frame_content, introFragment).commitAllowingStateLoss();
    }

    @Override
    public ManualInjector<Fragment> supportFragmentInjector() {
        return componentSource;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}