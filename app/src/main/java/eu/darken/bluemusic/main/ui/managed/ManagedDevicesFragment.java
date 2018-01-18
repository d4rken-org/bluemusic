package eu.darken.bluemusic.main.ui.managed;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
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

import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.bluetooth.ui.BluetoothActivity;
import eu.darken.bluemusic.main.core.database.ManagedDevice;
import eu.darken.bluemusic.main.ui.MainActivity;
import eu.darken.bluemusic.main.ui.config.ConfigFragment;
import eu.darken.bluemusic.settings.ui.SettingsActivity;
import eu.darken.bluemusic.util.Check;
import eu.darken.ommvplib.base.OMMVPLib;
import eu.darken.ommvplib.injection.InjectedPresenter;
import eu.darken.ommvplib.injection.PresenterInjectionCallback;


public class ManagedDevicesFragment extends Fragment implements ManagedDevicesPresenter.View, ManagedDevicesAdapter.Callback {

    @BindView(R.id.recyclerview) RecyclerView recyclerView;
    @BindView(R.id.empty_info) View emptyInfo;
    @BindView(R.id.bluetooth_disabled_container) View bluetoothDisabledContainer;
    @BindView(R.id.bluetooth_enabled_container) View bluetoothEnabledContainer;
    @BindView(R.id.fab) FloatingActionButton fab;
    @Inject ManagedDevicesPresenter presenter;

    Unbinder unbinder;
    private ManagedDevicesAdapter adapter;
    private boolean isProVersion = false;

    public static Fragment newInstance() {
        return new ManagedDevicesFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OMMVPLib.<ManagedDevicesPresenter.View, ManagedDevicesPresenter>builder()
                .presenterCallback(new PresenterInjectionCallback<>(this))
                .presenterSource(new InjectedPresenter<>(this))
                .attach(this);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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
        Check.notNull(getActivity());
        final ActionBar actionBar = ((MainActivity) getActivity()).getSupportActionBar();
        Check.notNull(actionBar);
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setTitle(R.string.app_name);
        actionBar.setSubtitle(null);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main, menu);
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
                //noinspection ConstantConditions
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.label_premium_version)
                        .setMessage(R.string.description_premium_upgrade_explanation)
                        .setIcon(R.drawable.ic_stars_white_24dp)
                        .setPositiveButton(R.string.action_upgrade, (dialogInterface, i) -> presenter.onUpgradeClicked(getActivity()))
                        .setNegativeButton(R.string.action_cancel, (dialogInterface, i) -> {})
                        .show();
                return true;
            case R.id.settings:
                //noinspection ConstantConditions
                startActivity(new Intent(getContext(), SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @OnClick(R.id.fab)
    public void onFabClicked(View fab) {
        //noinspection ConstantConditions
        startActivity(new Intent(getContext(), BluetoothActivity.class));
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
        //noinspection ConstantConditions
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
    public void onMusicVolumeAdjusted(ManagedDevice device, float percentage) {
        presenter.onUpdateMusicVolume(device, percentage);
    }

    @Override
    public void onCallVolumeAdjusted(ManagedDevice device, float percentage) {
        presenter.onUpdateCallVolume(device, percentage);
    }

    @Override
    public void onShowConfigScreen(ManagedDevice device) {
        //noinspection ConstantConditions
        getActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.frame_content, ConfigFragment.instantiate(device))
                .addToBackStack(null)
                .commit();
    }
}
