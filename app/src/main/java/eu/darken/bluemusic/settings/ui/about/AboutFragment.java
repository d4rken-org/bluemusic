package eu.darken.bluemusic.settings.ui.about;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;

import com.bugsnag.android.Bugsnag;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import eu.darken.bluemusic.ManualReport;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.util.Check;
import eu.darken.mvpbakery.base.MVPBakery;
import eu.darken.mvpbakery.base.ViewModelRetainer;
import eu.darken.mvpbakery.injection.InjectedPresenter;
import eu.darken.mvpbakery.injection.PresenterInjectionCallback;


public class AboutFragment extends PreferenceFragmentCompat implements AboutPresenter.View {
    private Preference uploadPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings_about);
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

        uploadPref = findPreference("about.debug.upload");
        uploadPref.setVisible(false);
        uploadPref.setOnPreferenceClickListener(preference -> {
            Bugsnag.notify(new ManualReport());
            Snackbar.make(getView(), "Done :) Now mail me!", Snackbar.LENGTH_SHORT).show();
            return true;
        });
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

    @Override
    public void showVersion(String version) {
        findPreference("about.version").setSummary(version);
    }

    @Override
    public void showInstallID(String id) {
        Preference pref = findPreference("about.installid");
        pref.setSummary(id);
        pref.setOnPreferenceClickListener(preference -> {
            ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("BVM Install ID", id);
            clipboard.setPrimaryClip(clip);
            Snackbar.make(getView(), R.string.message_copied_to_clipboard, Snackbar.LENGTH_LONG).show();
            uploadPref.setVisible(true);
            return true;
        });
    }
}
