package eu.darken.bluemusic.main.ui.config;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.bluetooth.core.FakeSpeakerDevice;
import eu.darken.bluemusic.main.core.audio.AudioStream;
import eu.darken.bluemusic.main.core.database.ManagedDevice;
import eu.darken.bluemusic.main.ui.MainActivity;
import eu.darken.bluemusic.util.AppTool;
import eu.darken.bluemusic.util.Check;
import eu.darken.bluemusic.util.ui.PreferenceView;
import eu.darken.bluemusic.util.ui.SwitchPreferenceView;
import eu.darken.mvpbakery.base.MVPBakery;
import eu.darken.mvpbakery.base.PresenterRetainer;
import eu.darken.mvpbakery.base.viewmodel.ViewModelRetainer;
import eu.darken.mvpbakery.injection.InjectedPresenter;
import eu.darken.mvpbakery.injection.PresenterInjectionCallback;
import timber.log.Timber;


public class ConfigFragment extends Fragment implements ConfigPresenter.View {
    private static final String ARG_ADDRESS = "device.address";
    private static final String ARG_NAME = "device.aliasorname";
    private ActionBar actionBar;
    private Unbinder unbinder;
    @BindView(R.id.pref_music_volume) SwitchPreferenceView prefMusicVolume;
    @BindView(R.id.pref_call_volume) SwitchPreferenceView prefCallVolume;
    @BindView(R.id.pref_autoplay_enabled) SwitchPreferenceView prefAutoPlay;
    @BindView(R.id.pref_launch_app) PreferenceView prefLaunchApp;
    @BindView(R.id.pref_reaction_delay) PreferenceView prefReactionDelay;
    @BindView(R.id.pref_adjustment_delay) PreferenceView prefAdjustmentDelay;
    @BindView(R.id.pref_rename) PreferenceView prefRename;
    @BindView(R.id.pref_delete) PreferenceView prefDelete;
    @Inject ConfigPresenter presenter;

    public static Fragment instantiate(ManagedDevice device) {
        Bundle bundle = new Bundle();
        bundle.putString(ARG_ADDRESS, device.getAddress());
        bundle.putString(ARG_NAME, device.tryGetAlias());
        ConfigFragment fragment = new ConfigFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        android.view.View layout = inflater.inflate(R.layout.fragment_layout_config, container, false);
        unbinder = ButterKnife.bind(this, layout);
        return layout;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        prefMusicVolume.setOnCheckedChangedListener((v, checked) -> presenter.onToggleMusicVolume());
        prefCallVolume.setOnCheckedChangedListener((v, checked) -> presenter.onToggleCallVolume());
        prefAutoPlay.setOnCheckedChangedListener((v, checked) -> v.setChecked(presenter.onToggleAutoPlay()));
        prefLaunchApp.setOnClickListener(v -> {
            presenter.onLaunchAppClicked();
            Snackbar.make(Check.notNull(getView()), R.string.label_just_a_moment_please, Snackbar.LENGTH_SHORT).show();
        });
        prefReactionDelay.setOnClickListener(v -> presenter.onEditReactionDelayClicked());
        prefAdjustmentDelay.setOnClickListener(v -> presenter.onEditAdjustmentDelayClicked());
        prefRename.setOnClickListener(v -> presenter.onRenameClicked());
        prefDelete.setOnClickListener(v -> presenter.onDeleteDevice());
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        MVPBakery.<ConfigPresenter.View, ConfigPresenter>builder()
                .addPresenterCallback(new PresenterRetainer.Callback<ConfigPresenter.View, ConfigPresenter>() {
                    @Override
                    public void onPresenterCreated(ConfigPresenter presenter) {
                        Check.notNull(getArguments());
                        final String address = getArguments().getString(ARG_ADDRESS);
                        presenter.setDevice(address);
                    }

                    @Override
                    public void onPresenterDestroyed() {

                    }
                })
                .addPresenterCallback(new PresenterInjectionCallback<>(this))
                .presenterRetainer(new ViewModelRetainer<>(this))
                .presenterFactory(new InjectedPresenter<>(this))
                .attach(this);
        super.onActivityCreated(savedInstanceState);

        Check.notNull(getActivity());
        actionBar = ((MainActivity) getActivity()).getSupportActionBar();
        Check.notNull(actionBar);
        actionBar.setDisplayHomeAsUpEnabled(true);

        Check.notNull(getArguments());
        actionBar.setTitle(getArguments().getString(ARG_NAME));
    }

    @Override
    public void onDestroyView() {
        if (unbinder != null) unbinder.unbind();
        super.onDestroyView();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Check.notNull(getActivity());
        switch (item.getItemId()) {
            case android.R.id.home:
                getActivity().getSupportFragmentManager().popBackStack();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void updateProState(boolean isPro) {
        prefAutoPlay.setIcon(isPro ? R.drawable.ic_play_circle_outline_white_24dp : R.drawable.ic_stars_white_24dp);
        prefAutoPlay.setDescription(getString(R.string.description_autoplay) + (isPro ? "" : ("\n[" + getString(R.string.label_premium_version_required) + "]")));

        prefRename.setIcon(isPro ? R.drawable.ic_mode_edit_white_24dp : R.drawable.ic_stars_white_24dp);
        prefRename.setDescription(getString(R.string.description_rename_device) + (isPro ? "" : ("\n[" + getString(R.string.label_premium_version_required) + "]")));
    }

    @Override
    public void updateDevice(ManagedDevice device) {
        Check.notNull(getArguments());
        String alias = device.tryGetAlias();
        actionBar.setTitle(alias);
        String name = device.getName();
        if (!alias.equals(name)) actionBar.setSubtitle(name);

        prefMusicVolume.setChecked(device.getVolume(AudioStream.Type.MUSIC) != null);
        prefMusicVolume.setVisibility(View.VISIBLE);

        prefCallVolume.setChecked(device.getVolume(AudioStream.Type.CALL) != null);
        prefCallVolume.setVisibility(View.VISIBLE);

        prefAutoPlay.setChecked(device.isAutoPlayEnabled());
        prefAutoPlay.setVisibility(View.VISIBLE);

        prefLaunchApp.setVisibility(View.VISIBLE);

        if (device.getLaunchPkg() != null) {
            String label = device.getLaunchPkg();
            try {
                label = AppTool.getLabel(getContext(), device.getLaunchPkg());
            } catch (PackageManager.NameNotFoundException e) {
                Timber.e(e);
            }
            prefLaunchApp.setDescription(label);
        } else prefLaunchApp.setDescription(getString(R.string.description_launch_app));

        prefReactionDelay.setVisibility(View.VISIBLE);
        prefAdjustmentDelay.setVisibility(View.VISIBLE);

        prefRename.setVisibility(device.getAddress().equals(FakeSpeakerDevice.ADDR) ? View.GONE : View.VISIBLE);
        prefDelete.setVisibility(View.VISIBLE);
    }

    @Override
    public void showRequiresPro() {
        new AlertDialog.Builder(Check.notNull(getContext()))
                .setTitle(R.string.label_premium_version)
                .setMessage(R.string.description_premium_required_this_extra_option)
                .setIcon(R.drawable.ic_stars_white_24dp)
                .setPositiveButton(R.string.action_upgrade, (dialogInterface, i) -> presenter.onPurchaseUpgrade(getActivity()))
                .setNegativeButton(R.string.action_cancel, (dialogInterface, i) -> {})
                .show();
    }

    @SuppressLint("InflateParams")
    @Override
    public void showReactionDelayDialog(long delay) {
        View container = getLayoutInflater().inflate(R.layout.view_dialog_delay, null);
        EditText input = container.findViewById(R.id.input);
        input.setText(String.valueOf(delay));
        new AlertDialog.Builder(Check.notNull(getContext()))
                .setTitle(R.string.label_reaction_delay)
                .setMessage(R.string.description_reaction_delay)
                .setIcon(R.drawable.ic_timer_white_24dp)
                .setView(container)
                .setPositiveButton(R.string.action_set, (dialogInterface, i) -> {
                    try {
                        final long newDelay = Long.parseLong(input.getText().toString());
                        presenter.onEditReactionDelay(newDelay);
                    } catch (NumberFormatException e) {
                        Timber.e(e);
                        Check.notNull(getView());
                        Snackbar.make(getView(), R.string.label_invalid_input, Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton(R.string.action_reset, (dialogInterface, i) -> presenter.onEditReactionDelay(-1))
                .setNegativeButton(R.string.action_cancel, (dialogInterface, i) -> {})
                .show();
    }

    @SuppressLint("InflateParams")
    @Override
    public void showAdjustmentDelayDialog(long delay) {
        Check.notNull(getContext());
        View container = getLayoutInflater().inflate(R.layout.view_dialog_delay, null);
        EditText input = container.findViewById(R.id.input);
        input.setText(String.valueOf(delay));
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.label_adjustment_delay)
                .setMessage(R.string.description_adjustment_delay)
                .setIcon(R.drawable.ic_av_timer_white_24dp)
                .setView(container)
                .setPositiveButton(R.string.action_set, (dialogInterface, i) -> {
                    try {
                        final long newDelay = Long.parseLong(input.getText().toString());
                        presenter.onEditAdjustmentDelay(newDelay);
                    } catch (NumberFormatException e) {
                        Timber.e(e);
                        Check.notNull(getView());
                        Snackbar.make(getView(), R.string.label_invalid_input, Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton(R.string.action_reset, (dialogInterface, i) -> presenter.onEditAdjustmentDelay(-1))
                .setNegativeButton(R.string.action_cancel, (dialogInterface, i) -> { })
                .show();
    }

    @SuppressLint("InflateParams")
    @Override
    public void showRenameDialog(String current) {
        Check.notNull(getContext());
        View container = getLayoutInflater().inflate(R.layout.view_dialog_rename, null);
        EditText input = container.findViewById(R.id.input);
        input.setText(current);
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.label_rename_device)
                .setView(container)
                .setPositiveButton(R.string.action_set, (dialogInterface, i) -> presenter.onRenameDevice(input.getText().toString()))
                .setNeutralButton(R.string.action_reset, (dialogInterface, i) -> presenter.onRenameDevice(null))
                .setNegativeButton(R.string.action_cancel, (dialogInterface, i) -> { })
                .show();
    }

    @Override
    public void showAppSelectionDialog(List<AppTool.Item> items) {
        LaunchAppAdapter adapter = new LaunchAppAdapter(Check.notNull(getContext()), items);
        new AlertDialog.Builder(getContext())
                .setAdapter(adapter,
                        (dialog, pos) -> presenter.onLaunchAppSelected(adapter.getItem(pos)))
                .show();
    }

    @Override
    public void finishScreen() {
        Check.notNull(getActivity());
        getActivity().getSupportFragmentManager().popBackStack();
    }
}
