package eu.darken.bluemusic.screens.managed;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.core.database.ManagedDevice;
import eu.darken.bluemusic.core.settings.Settings;
import eu.darken.bluemusic.screens.MainActivity;
import eu.darken.bluemusic.screens.about.AboutFragment;
import eu.darken.bluemusic.screens.devices.DevicesFragment;
import eu.darken.bluemusic.screens.settings.SettingsFragment;
import eu.darken.bluemusic.util.Preconditions;
import eu.darken.ommvplib.injection.ComponentPresenterSupportFragment;
import timber.log.Timber;


public class ManagedDevicesFragment extends ComponentPresenterSupportFragment<ManagedDevicesPresenter.View, ManagedDevicesPresenter, ManagedDevicesComponent>
        implements ManagedDevicesPresenter.View, ManagedDevicesAdapter.Callback {

    @BindView(R.id.recyclerview) RecyclerView recyclerView;
    @BindView(R.id.empty_info) View emptyInfo;
    @BindView(R.id.bluetooth_disabled_container) View bluetoothDisabledContainer;
    @BindView(R.id.bluetooth_enabled_container) View bluetoothEnabledContainer;
    @BindView(R.id.fab) FloatingActionButton fab;

    Unbinder unbinder;
    private ManagedDevicesAdapter adapter;
    private boolean isProVersion = false;

    public static Fragment newInstance() {
        return new ManagedDevicesFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public Class<ManagedDevicesPresenter> getTypeClazz() {
        return ManagedDevicesPresenter.class;
    }

    @Nullable
    @Override
    public android.view.View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        android.view.View layout = inflater.inflate(R.layout.fragment_layout_managed_devices, container, false);
        unbinder = ButterKnife.bind(this, layout);
        return layout;
    }

    @Override
    public void onViewCreated(android.view.View view, @Nullable Bundle savedInstanceState) {
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        adapter = new ManagedDevicesAdapter(this);
        recyclerView.setAdapter(adapter);
        super.onViewCreated(view, savedInstanceState);
    }


    @Override
    public void onDestroyView() {
        if (unbinder != null) unbinder.unbind();
        super.onDestroyView();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final ActionBar actionBar = ((MainActivity) getActivity()).getSupportActionBar();
        Preconditions.checkNotNull(actionBar);
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setTitle(R.string.app_name);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_settings, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.upgrade).setVisible(!isProVersion);
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.upgrade:
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.label_premium_version)
                        .setMessage(R.string.desc_premium_upgrade_explanation)
                        .setIcon(R.drawable.ic_stars_white_24dp)
                        .setPositiveButton(R.string.action_upgrade, (dialogInterface, i) -> getPresenter().onUpgradeClicked(getActivity()))
                        .setNegativeButton(R.string.action_cancel, (dialogInterface, i) -> {})
                        .show();
                return true;
            case R.id.settings:
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.frame_content, new SettingsFragment())
                        .addToBackStack(null)
                        .commit();
                return true;
            case R.id.about:
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.frame_content, new AboutFragment())
                        .addToBackStack(null)
                        .commit();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @OnClick(R.id.fab)
    public void onFabClicked(View fab) {
        getActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.frame_content, new DevicesFragment())
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void displayBluetoothState(boolean enabled) {
        bluetoothDisabledContainer.setVisibility(enabled ? View.GONE : View.VISIBLE);
        bluetoothEnabledContainer.setVisibility(enabled ? View.VISIBLE : View.GONE);
        fab.setVisibility(enabled ? View.VISIBLE : View.GONE);
    }

    @Override
    public void updateUpgradeState(boolean isProVersion) {
        this.isProVersion = isProVersion;
        getActivity().invalidateOptionsMenu();
    }

    @Override
    public void displayDevices(List<ManagedDevice> managedDevices) {
        adapter.setData(managedDevices);
        adapter.notifyDataSetChanged();

        recyclerView.setVisibility(managedDevices.isEmpty() ? View.GONE : View.VISIBLE);
        emptyInfo.setVisibility(managedDevices.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDeleteDevice(ManagedDevice device) {
        getPresenter().onDeleteDevice(device);
    }

    @SuppressLint("InflateParams")
    @Override
    public void onEditReactionDelay(ManagedDevice device) {
        View container = getLayoutInflater().inflate(R.layout.view_dialog_delay, null);
        EditText input = container.findViewById(R.id.input);
        input.setText(device.getActionDelay() != null ? String.valueOf(device.getActionDelay()) : String.valueOf(Settings.DEFAULT_REACTION_DELAY));
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.label_reaction_delay)
                .setMessage(R.string.desc_reaction_delay)
                .setIcon(R.drawable.ic_timer_white_24dp)
                .setView(container)
                .setPositiveButton(R.string.action_set, (dialogInterface, i) -> {
                    try {
                        final long delay = Long.parseLong(input.getText().toString());
                        getPresenter().onEditReactionDelay(device, delay);
                    } catch (NumberFormatException e) {
                        Timber.e(e);
                        Preconditions.checkNotNull(getView());
                        Snackbar.make(getView(), R.string.label_invalid_input, Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton(R.string.action_reset, (dialogInterface, i) -> getPresenter().onEditReactionDelay(device, -1))
                .setNegativeButton(R.string.action_cancel, (dialogInterface, i) -> {
                })
                .show();
    }

    @SuppressLint("InflateParams")
    @Override
    public void onEditAdjustmentDelay(ManagedDevice device) {
        View container = getLayoutInflater().inflate(R.layout.view_dialog_delay, null);
        EditText input = container.findViewById(R.id.input);
        input.setText(device.getAdjustmentDelay() != null ? String.valueOf(device.getAdjustmentDelay()) : String.valueOf(Settings.DEFAULT_ADJUSTMENT_DELAY));
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.label_adjustment_delay)
                .setMessage(R.string.desc_adjustment_delay)
                .setIcon(R.drawable.ic_av_timer_white_24dp)
                .setView(container)
                .setPositiveButton(R.string.action_set, (dialogInterface, i) -> {
                    try {
                        final long delay = Long.parseLong(input.getText().toString());
                        getPresenter().onEditAdjustmentDelay(device, delay);
                    } catch (NumberFormatException e) {
                        Timber.e(e);
                        Preconditions.checkNotNull(getView());
                        Snackbar.make(getView(), R.string.label_invalid_input, Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton(R.string.action_reset, (dialogInterface, i) -> getPresenter().onEditAdjustmentDelay(device, -1))
                .setNegativeButton(R.string.action_cancel, (dialogInterface, i) -> {
                })
                .show();
    }

    @Override
    public void onMusicVolumeAdjusted(ManagedDevice device, float percentage) {
        getPresenter().onUpdateMusicVolume(device, percentage);
    }

    @Override
    public void onCallVolumeAdjusted(ManagedDevice device, float percentage) {
        getPresenter().onUpdateCallVolume(device, percentage);
    }

    @Override
    public void onToggleMusicVolumeAction(ManagedDevice device) {
        getPresenter().onToggleMusicVolumeAction(device);
    }

    @Override
    public void onToggleCallVolumeAction(ManagedDevice device) {
        getPresenter().onToggleCallVolumeAction(device);
    }

    @Override
    public void onToggleAutoPlay(ManagedDevice item) {
        if (isProVersion) {
            getPresenter().onToggleAutoplay(item);
        } else {
            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.label_premium_version)
                    .setMessage(R.string.desc_premium_required_this_extra_option)
                    .setIcon(R.drawable.ic_stars_white_24dp)
                    .setPositiveButton(R.string.action_upgrade, (dialogInterface, i) -> getPresenter().onUpgradeClicked(getActivity()))
                    .setNegativeButton(R.string.action_cancel, (dialogInterface, i) -> {})
                    .show();
        }
    }
}
