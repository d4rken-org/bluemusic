package eu.darken.bluemusic.settings.ui.about;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.view.MenuItem;

import java.util.Locale;

import eu.darken.bluemusic.BuildConfig;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.util.Check;
import eu.darken.mvpbakery.base.MVPBakery;
import eu.darken.mvpbakery.base.viewmodel.ViewModelRetainer;
import eu.darken.mvpbakery.injection.InjectedPresenter;
import eu.darken.mvpbakery.injection.PresenterInjectionCallback;
import timber.log.Timber;


public class AboutFragment extends PreferenceFragmentCompat implements AboutPresenter.View {
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
            //noinspection ConstantConditions
            PackageInfo info = getContext().getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0);
            versionPref.setSummary(String.format(Locale.US, "Version %s (%d)", info.versionName, info.versionCode));
        } catch (PackageManager.NameNotFoundException e) {
            Timber.e(e);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        MVPBakery.<AboutPresenter.View, AboutPresenter>builder()
                .presenterFactory(new InjectedPresenter<>(this))
                .presenterRetainer(new ViewModelRetainer<>(this))
                .addPresenterCallback(new PresenterInjectionCallback<>(this))
                .attach(this);
        super.onActivityCreated(savedInstanceState);
        //noinspection ConstantConditions
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        Check.notNull(actionBar);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.label_about);
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
