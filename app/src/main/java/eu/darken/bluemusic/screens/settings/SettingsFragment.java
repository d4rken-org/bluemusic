package eu.darken.bluemusic.screens.settings;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.Preference;
import android.view.MenuItem;

import eu.darken.bluemusic.R;
import eu.darken.bluemusic.screens.MainActivity;
import eu.darken.bluemusic.util.Preconditions;
import eu.darken.ommvplib.injection.ComponentPresenterPreferenceFragment;
import timber.log.Timber;


public class SettingsFragment extends ComponentPresenterPreferenceFragment<SettingsPresenter.View, SettingsPresenter, SettingsComponent> implements SettingsPresenter.View {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings);
        findPreference("core.volume.delay.systemfudge").setOnPreferenceChangeListener((preference, newValue) -> {
            try {
                Timber.i("Delay: %d", Long.parseLong((String) newValue));
                return true;
            } catch (Exception e) {
                Timber.e(e, null);
                return false;
            }
        });
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final ActionBar actionBar = ((MainActivity) getActivity()).getSupportActionBar();
        Preconditions.checkNotNull(actionBar);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.label_settings);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if ("core.volume.enabled".equals(preference.getKey())) {
            getPresenter().toggleService(((CheckBoxPreference) preference).isChecked());
            return true;
        } else if ("core.bugreporting.enabled".equals(preference.getKey())) {
            preference.setSummary(((CheckBoxPreference) preference).isChecked() ? ":)" : ":(");
            return true;
        } else return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getActivity().getSupportFragmentManager().popBackStack();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public Class<? extends SettingsPresenter> getTypeClazz() {
        return SettingsPresenter.class;
    }
}
