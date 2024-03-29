package eu.darken.bluemusic.settings.ui.general;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.settings.core.Settings;
import eu.darken.bluemusic.settings.ui.about.AboutFragment;
import eu.darken.bluemusic.settings.ui.advanced.AdvancedFragment;
import eu.darken.bluemusic.util.Check;
import eu.darken.mvpbakery.base.MVPBakery;
import eu.darken.mvpbakery.base.ViewModelRetainer;
import eu.darken.mvpbakery.injection.InjectedPresenter;
import eu.darken.mvpbakery.injection.PresenterInjectionCallback;


public class SettingsFragment extends PreferenceFragmentCompat implements SettingsPresenter.View {
    @Inject Settings settings;
    @Inject SettingsPresenter presenter;

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
        addPreferencesFromResource(R.xml.settings_general);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        MVPBakery.<SettingsPresenter.View, SettingsPresenter>builder()
                .presenterFactory(new InjectedPresenter<>(this))
                .presenterRetainer(new ViewModelRetainer<>(this))
                .addPresenterCallback(new PresenterInjectionCallback<>(this))
                .attach(this);
        super.onActivityCreated(savedInstanceState);
        //noinspection ConstantConditions
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        Check.notNull(actionBar);
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
        visibleAdjustments.setIcon(isPro ? null : ContextCompat.getDrawable(Check.notNull(getContext()), R.drawable.ic_stars_white_24dp));
        visibleAdjustments.setSummary(getString(R.string.description_visible_volume_adjustments) + (isPro ? "" : ("\n[" + getString(R.string.label_premium_version_required) + "]")));
        visibleAdjustments.setOnPreferenceClickListener(preference -> {
            if (isPro) return false;
            else {
                visibleAdjustments.setChecked(!visibleAdjustments.isChecked());
                showRequiresPremiumDialog();
                return true;
            }
        });

        CheckBoxPreference autoSave = (CheckBoxPreference) findPreference(Settings.PREFKEY_SPEAKER_AUTOSAVE);
        autoSave.setIcon(isPro ? null : ContextCompat.getDrawable(Check.notNull(getContext()), R.drawable.ic_stars_white_24dp));
        autoSave.setSummary(getString(R.string.description_speaker_autosave) + (isPro ? "" : ("\n[" + getString(R.string.label_premium_version_required) + "]")));
        autoSave.setOnPreferenceClickListener(preference -> {
            if (isPro) return false;
            else {
                autoSave.setChecked(!autoSave.isChecked());
                showRequiresPremiumDialog();
                return true;
            }
        });
    }

    void showRequiresPremiumDialog() {
        new AlertDialog.Builder(Check.notNull(getContext()))
                .setTitle(R.string.label_premium_version)
                .setMessage(R.string.description_premium_required_this_extra_option)
                .setIcon(R.drawable.ic_stars_white_24dp)
                .setPositiveButton(R.string.action_upgrade, (dialogInterface, i) -> presenter.onUpgradeClicked(getActivity()))
                .setNegativeButton(R.string.action_cancel, (dialogInterface, i) -> {})
                .show();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (Settings.PREFKEY_AUTOPLAY_KEYCODE.equals(preference.getKey())) {
            List<Pair<Integer, String>> pairs = new ArrayList<>();

            pairs.add(Pair.create(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, getString(R.string.label_keyevent_playpause)));
            pairs.add(Pair.create(KeyEvent.KEYCODE_MEDIA_PLAY, getString(R.string.label_keyevent_play)));
            pairs.add(Pair.create(KeyEvent.KEYCODE_MEDIA_NEXT, getString(R.string.label_keyevent_next)));


            final int currentKeyCode = settings.getAutoplayKeycode();
            int selected = 0;
            String[] labels = new String[pairs.size()];
            for (int i = 0; i < pairs.size(); i++) {
                labels[i] = pairs.get(i).second;
                if (pairs.get(i).first == currentKeyCode) selected = i;
            }
            new AlertDialog.Builder(Check.notNull(getContext()))
                    .setSingleChoiceItems(labels, selected, (dialogInterface, pos) -> {
                        settings.setAutoplayKeycode(pairs.get(pos).first);
                        dialogInterface.dismiss();
                    })
                    .show();
            return true;
        } else if (Settings.PREFKEY_BUGREPORTING.equals(preference.getKey())) {
            preference.setSummary(((CheckBoxPreference) preference).isChecked() ? ":)" : ":(");
            return true;
        } else if ("core.advanced".equals(preference.getKey())) {
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.frame_content, AdvancedFragment.newInstance())
                    .addToBackStack(null)
                    .commit();
            return true;
        } else {
            return super.onPreferenceTreeClick(preference);
        }
    }
}
