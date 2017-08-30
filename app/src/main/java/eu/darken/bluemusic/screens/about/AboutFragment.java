package eu.darken.bluemusic.screens.about;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.preference.Preference;
import android.view.MenuItem;

import java.util.Locale;

import eu.darken.bluemusic.BuildConfig;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.screens.MainActivity;
import eu.darken.bluemusic.util.Preconditions;
import eu.darken.ommvplib.injection.ComponentPresenterPreferenceFragment;
import timber.log.Timber;


public class AboutFragment extends ComponentPresenterPreferenceFragment<AboutPresenter.View, AboutPresenter, AboutComponent> implements AboutPresenter.View {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.about);
        final Preference versionPref = findPreference("about.version");
        try {
            final PackageInfo info = getContext().getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0);
            versionPref.setSummary(String.format(Locale.US, "Version %s (%d)", info.versionName, info.versionCode));
        } catch (PackageManager.NameNotFoundException e) {
            Timber.e(e, null);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final ActionBar actionBar = ((MainActivity) getActivity()).getSupportActionBar();
        Preconditions.checkNotNull(actionBar);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.label_about);
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
    public Class<? extends AboutPresenter> getTypeClazz() {
        return AboutPresenter.class;
    }
}
