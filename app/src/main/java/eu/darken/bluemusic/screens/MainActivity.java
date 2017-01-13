package eu.darken.bluemusic.screens;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.MenuItem;

import butterknife.ButterKnife;
import eu.darken.bluemusic.App;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.screens.volumes.VolumeManagerFragment;
import eu.darken.bluemusic.util.ui.BaseActivity;


public class MainActivity extends BaseActivity {
    static final String TAG = App.prefixTag("MainActivity");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_layout_main);
        ButterKnife.bind(this);

        Fragment introFragment = getSupportFragmentManager().findFragmentById(R.id.frame_content);
        if (introFragment == null) introFragment = VolumeManagerFragment.newInstance();
        getSupportFragmentManager().beginTransaction().replace(R.id.frame_content, introFragment).commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}