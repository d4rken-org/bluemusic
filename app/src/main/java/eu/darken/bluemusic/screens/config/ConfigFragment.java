package eu.darken.bluemusic.screens.config;

import android.annotation.SuppressLint;
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

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.core.database.ManagedDevice;
import eu.darken.bluemusic.screens.MainActivity;
import eu.darken.bluemusic.util.Preconditions;
import eu.darken.bluemusic.util.ui.PreferenceView;
import eu.darken.bluemusic.util.ui.SwitchPreferenceView;
import eu.darken.ommvplib.injection.ComponentPresenterSupportFragment;
import timber.log.Timber;


public class ConfigFragment extends ComponentPresenterSupportFragment<ConfigPresenter.View, ConfigPresenter, ConfigComponent> implements ConfigPresenter.View {
    private static final String ARG_ADDRESS = "device.address";
    private static final String ARG_NAME = "device.aliasorname";
    private ActionBar actionBar;
    private Unbinder unbinder;
    @BindView(R.id.pref_music_volume) SwitchPreferenceView prefMusicVolume;
    @BindView(R.id.pref_call_volume) SwitchPreferenceView prefCallVolume;
    @BindView(R.id.pref_autoplay_enabled) SwitchPreferenceView prefAutoPlay;
    @BindView(R.id.pref_reaction_delay) PreferenceView prefReactionDelay;
    @BindView(R.id.pref_adjustment_delay) PreferenceView prefAdjustmentDelay;
    @BindView(R.id.pref_rename) PreferenceView prefRename;
    @BindView(R.id.pref_delete) PreferenceView prefDelete;

    public static Fragment instantiate(ManagedDevice device) {
        Bundle bundle = new Bundle();
        bundle.putString(ARG_ADDRESS, device.getAddress());
        bundle.putString(ARG_NAME, device.tryGetAlias());
        ConfigFragment fragment = new ConfigFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public Class<? extends ConfigPresenter> getTypeClazz() {
        return ConfigPresenter.class;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onPresenterReady(@NonNull ConfigPresenter presenter) {
        Preconditions.checkNotNull(getArguments());
        final String address = getArguments().getString(ARG_ADDRESS);
        getPresenter().setDevice(address);
        super.onPresenterReady(presenter);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        android.view.View layout = inflater.inflate(R.layout.fragment_layout_config, container, false);
        unbinder = ButterKnife.bind(this, layout);
        return layout;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        prefMusicVolume.setOnCheckedChangedListener((v, checked) -> getPresenter().onToggleMusicVolume());
        prefCallVolume.setOnCheckedChangedListener((v, checked) -> getPresenter().onToggleCallVolume());
        prefAutoPlay.setOnCheckedChangedListener((v, checked) -> getPresenter().onToggleAutoPlay());
        prefReactionDelay.setOnClickListener(v -> getPresenter().onEditReactionDelayClicked());
        prefAdjustmentDelay.setOnClickListener(v -> getPresenter().onEditAdjustmentDelayClicked());
        prefRename.setOnClickListener(v -> getPresenter().onRenameClicked());
        prefDelete.setOnClickListener(v -> getPresenter().onDeleteDevice());
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Preconditions.checkNotNull(getActivity());
        actionBar = ((MainActivity) getActivity()).getSupportActionBar();
        Preconditions.checkNotNull(actionBar);
        actionBar.setDisplayHomeAsUpEnabled(true);

        Preconditions.checkNotNull(getArguments());
        actionBar.setTitle(getArguments().getString(ARG_NAME));
    }

    @Override
    public void onDestroyView() {
        if (unbinder != null) unbinder.unbind();
        super.onDestroyView();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Preconditions.checkNotNull(getActivity());
        switch (item.getItemId()) {
            case android.R.id.home:
                getActivity().getSupportFragmentManager().popBackStack();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void updateDevice(ManagedDevice device) {
        Preconditions.checkNotNull(getArguments());
        String alias = device.tryGetAlias();
        actionBar.setTitle(alias);
        String name = device.getName();
        if (!alias.equals(name)) actionBar.setSubtitle(name);

        prefMusicVolume.setChecked(device.getMusicVolume() != null);
        prefMusicVolume.setVisibility(View.VISIBLE);

        prefCallVolume.setChecked(device.getCallVolume() != null);
        prefCallVolume.setVisibility(View.VISIBLE);

        prefAutoPlay.setChecked(device.isAutoPlayEnabled());
        prefAutoPlay.setVisibility(View.VISIBLE);

        prefReactionDelay.setVisibility(View.VISIBLE);
        prefAdjustmentDelay.setVisibility(View.VISIBLE);

        prefRename.setVisibility(View.VISIBLE);
        prefDelete.setVisibility(View.VISIBLE);
    }

    @Override
    public void showRequiresPro() {
        Preconditions.checkNotNull(getContext());
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.label_premium_version)
                .setMessage(R.string.desc_premium_required_this_extra_option)
                .setIcon(R.drawable.ic_stars_white_24dp)
                .setPositiveButton(R.string.action_upgrade, (dialogInterface, i) -> getPresenter().onPurchaseUpgrade(getActivity()))
                .setNegativeButton(R.string.action_cancel, (dialogInterface, i) -> {})
                .show();
    }

    @SuppressLint("InflateParams")
    @Override
    public void showReactionDelayDialog(long delay) {
        Preconditions.checkNotNull(getContext());
        View container = getLayoutInflater().inflate(R.layout.view_dialog_delay, null);
        EditText input = container.findViewById(R.id.input);
        input.setText(String.valueOf(delay));
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.label_reaction_delay)
                .setMessage(R.string.description_reaction_delay)
                .setIcon(R.drawable.ic_timer_white_24dp)
                .setView(container)
                .setPositiveButton(R.string.action_set, (dialogInterface, i) -> {
                    try {
                        final long newDelay = Long.parseLong(input.getText().toString());
                        getPresenter().onEditReactionDelay(newDelay);
                    } catch (NumberFormatException e) {
                        Timber.e(e);
                        Preconditions.checkNotNull(getView());
                        Snackbar.make(getView(), R.string.label_invalid_input, Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton(R.string.action_reset, (dialogInterface, i) -> getPresenter().onEditReactionDelay(-1))
                .setNegativeButton(R.string.action_cancel, (dialogInterface, i) -> {})
                .show();
    }

    @SuppressLint("InflateParams")
    @Override
    public void showAdjustmentDelayDialog(long delay) {
        Preconditions.checkNotNull(getContext());
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
                        getPresenter().onEditAdjustmentDelay(newDelay);
                    } catch (NumberFormatException e) {
                        Timber.e(e);
                        Preconditions.checkNotNull(getView());
                        Snackbar.make(getView(), R.string.label_invalid_input, Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton(R.string.action_reset, (dialogInterface, i) -> getPresenter().onEditAdjustmentDelay(-1))
                .setNegativeButton(R.string.action_cancel, (dialogInterface, i) -> { })
                .show();
    }

    @SuppressLint("InflateParams")
    @Override
    public void showRenameDialog(String current) {
        Preconditions.checkNotNull(getContext());
        View container = getLayoutInflater().inflate(R.layout.view_dialog_rename, null);
        EditText input = container.findViewById(R.id.input);
        input.setText(current);
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.label_rename_device)
                .setView(container)
                .setPositiveButton(R.string.action_set, (dialogInterface, i) -> getPresenter().onRenameDevice(input.getText().toString()))
                .setNeutralButton(R.string.action_reset, (dialogInterface, i) -> getPresenter().onRenameDevice(null))
                .setNegativeButton(R.string.action_cancel, (dialogInterface, i) -> { })
                .show();
    }

    @Override
    public void finishScreen() {
        Preconditions.checkNotNull(getActivity());
        getActivity().getSupportFragmentManager().popBackStack();
    }
}
