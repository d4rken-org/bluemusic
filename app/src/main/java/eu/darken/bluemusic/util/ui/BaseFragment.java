package eu.darken.bluemusic.util.ui;

import android.support.v4.app.Fragment;

import eu.darken.bluemusic.App;


public class BaseFragment extends Fragment {

    @Override
    public void onDestroy() {
        super.onDestroy();
        App.getRefWatcher().watch(this);
    }
}
