package eu.darken.bluemusic.bluetooth.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.MenuItem;

import javax.inject.Inject;

import butterknife.ButterKnife;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.bluetooth.ui.discover.DiscoverFragment;
import eu.darken.ommvplib.injection.ComponentPresenterActivity;
import eu.darken.ommvplib.injection.ComponentSource;
import eu.darken.ommvplib.injection.ManualInjector;
import eu.darken.ommvplib.injection.fragment.support.HasManualSupportFragmentInjector;


public class BluetoothActivity extends ComponentPresenterActivity<BluetoothActivityPresenter.View, BluetoothActivityPresenter, BluetoothActivityComponent>
        implements BluetoothActivityPresenter.View, HasManualSupportFragmentInjector {

    @Inject ComponentSource<Fragment> componentSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.BaseAppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_layout_bluetooth);
        ButterKnife.bind(this);

        Fragment introFragment = getSupportFragmentManager().findFragmentById(R.id.frame_content);
        if (introFragment == null) introFragment = DiscoverFragment.newInstance();
        getSupportFragmentManager().beginTransaction().replace(R.id.frame_content, introFragment).commit();
    }

    @Override
    public Class<? extends BluetoothActivityPresenter> getTypeClazz() {
        return BluetoothActivityPresenter.class;
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