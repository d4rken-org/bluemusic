package eu.darken.bluetoothmanager.util.ui;

import android.support.v4.app.Fragment;

import eu.darken.bluetoothmanager.App;


public class BaseFragment extends Fragment {

    @Override
    public void onDestroy() {
        super.onDestroy();
        App.getRefWatcher().watch(this);
    }
}
