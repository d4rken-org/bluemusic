package eu.darken.bluemusic.settings.ui.general;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.Pair;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.Preference;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import eu.darken.bluemusic.R;
import eu.darken.bluemusic.settings.core.Settings;
import eu.darken.bluemusic.settings.ui.about.AboutFragment;
import eu.darken.bluemusic.util.Preconditions;
import eu.darken.ommvplib.injection.ComponentPresenterPreferenceFragment;


public class SettingsFragment extends ComponentPresenterPreferenceFragment<SettingsPresenter.View, SettingsPresenter, SettingsComponent> implements SettingsPresenter.View {
    @Inject Settings settings;

    public static Fragment newInstance() {
        return new SettingsFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        //noinspection ConstantConditions
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        Preconditions.checkNotNull(actionBar);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.label_settings);
        actionBar.setSubtitle(null);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_settings, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                //noinspection ConstantConditions
                getActivity().finish();
                return true;
            case R.id.about:
                //noinspection ConstantConditions
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.frame_content, new AboutFragment())
                        .addToBackStack(null)
                        .commit();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void updatePremiumState(boolean isPro) {
        CheckBoxPreference visibleAdjustments = (CheckBoxPreference) findPreference(Settings.PREFKEY_VISIBLE_ADJUSTMENTS);
        visibleAdjustments.setIcon(isPro ? null : ContextCompat.getDrawable(getContext(), R.drawable.ic_stars_white_24dp));
        visibleAdjustments.setSummary(getString(R.string.description_visible_volume_adjustments) + (isPro ? "" : ("\n[" + getString(R.string.label_premium_version_required) + "]")));
        visibleAdjustments.setOnPreferenceClickListener(preference -> {
            if (isPro) return false;
            else {
                visibleAdjustments.setChecked(!visibleAdjustments.isChecked());
                showRequiresPremiumDialog();
                return true;
            }
        });
    }

    void showRequiresPremiumDialog() {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.label_premium_version)
                .setMessage(R.string.description_premium_required_this_extra_option)
                .setIcon(R.drawable.ic_stars_white_24dp)
                .setPositiveButton(R.string.action_upgrade, (dialogInterface, i) -> getPresenter().onUpgradeClicked(getActivity()))
                .setNegativeButton(R.string.action_cancel, (dialogInterface, i) -> {})
                .show();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (Settings.PREFKEY_AUTOPLAY_KEYCODE.equals(preference.getKey())) {
            List<Pair<Integer, String>> pairs = new ArrayList<>();

            pairs.add(Pair.create(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Play-Pause"));
            pairs.add(Pair.create(KeyEvent.KEYCODE_MEDIA_PLAY, "Play"));
            pairs.add(Pair.create(KeyEvent.KEYCODE_MEDIA_NEXT, "Next"));


            final int currentKeyCode = settings.getAutoplayKeycode();
            int selected = 0;
            String[] labels = new String[pairs.size()];
            for (int i = 0; i < pairs.size(); i++) {
                labels[i] = pairs.get(i).second;
                if (pairs.get(i).first == currentKeyCode) selected = i;
            }
            new AlertDialog.Builder(getContext())
                    .setSingleChoiceItems(labels, selected, (dialogInterface, pos) -> {
                        settings.setAutoplayKeycode(pairs.get(pos).first);
                        dialogInterface.dismiss();
                    })
                    .show();
            return true;
        } else if ("core.bugreporting.enabled".equals(preference.getKey())) {
            preference.setSummary(((CheckBoxPreference) preference).isChecked() ? ":)" : ":(");
            return true;
        } else return super.onPreferenceTreeClick(preference);
    }

    @Override
    public Class<? extends SettingsPresenter> getTypeClazz() {
        return SettingsPresenter.class;
    }
}
