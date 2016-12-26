package eu.darken.bluetoothmanager.screens.settings;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;

import butterknife.BindView;
import butterknife.ButterKnife;
import eu.darken.bluetoothmanager.R;
import eu.darken.bluetoothmanager.util.ui.BaseActivity;

public class SettingsActivity extends BaseActivity {
    @BindView(R.id.toolbar) Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        ButterKnife.bind(this);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        if (savedInstanceState == null) {
            MainPreferencesFragment fragment = new MainPreferencesFragment();
            getFragmentManager().beginTransaction().replace(R.id.frame_settings, fragment).commit();
        }
    }

}
