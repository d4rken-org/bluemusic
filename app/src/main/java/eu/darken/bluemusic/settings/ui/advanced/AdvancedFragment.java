package eu.darken.bluemusic.settings.ui.advanced;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.view.MenuItem;

import javax.inject.Inject;

import eu.darken.bluemusic.R;
import eu.darken.bluemusic.settings.core.Settings;
import eu.darken.bluemusic.util.Check;
import eu.darken.mvpbakery.base.MVPBakery;
import eu.darken.mvpbakery.base.ViewModelRetainer;
import eu.darken.mvpbakery.injection.InjectedPresenter;
import eu.darken.mvpbakery.injection.PresenterInjectionCallback;


public class AdvancedFragment extends PreferenceFragmentCompat implements AdvancedPresenter.View {
    @Inject Settings settings;
    @Inject AdvancedPresenter presenter;

    public static Fragment newInstance() {
        return new AdvancedFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings_advanced);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        MVPBakery.<AdvancedPresenter.View, AdvancedPresenter>builder()
                .presenterFactory(new InjectedPresenter<>(this))
                .presenterRetainer(new ViewModelRetainer<>(this))
                .addPresenterCallback(new PresenterInjectionCallback<>(this))
                .attach(this);
        super.onActivityCreated(savedInstanceState);
        //noinspection ConstantConditions
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        Check.notNull(actionBar);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.label_advanced);
        actionBar.setSubtitle(null);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                //noinspection ConstantConditions
                getActivity().getSupportFragmentManager().popBackStack();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
