package eu.darken.bluemusic.main.ui.managed;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.bluetooth.ui.BluetoothActivity;
import eu.darken.bluemusic.main.core.audio.AudioStream;
import eu.darken.bluemusic.main.core.database.ManagedDevice;
import eu.darken.bluemusic.main.ui.MainActivity;
import eu.darken.bluemusic.main.ui.config.ConfigFragment;
import eu.darken.bluemusic.settings.ui.SettingsActivity;
import eu.darken.bluemusic.util.ActivityUtil;
import eu.darken.bluemusic.util.Check;
import eu.darken.mvpbakery.base.MVPBakery;
import eu.darken.mvpbakery.base.StateForwarder;
import eu.darken.mvpbakery.base.ViewModelRetainer;
import eu.darken.mvpbakery.injection.InjectedPresenter;
import eu.darken.mvpbakery.injection.PresenterInjectionCallback;


public class ManagedDevicesFragment extends Fragment implements ManagedDevicesPresenter.View, ManagedDevicesAdapter.Callback {

    @BindView(R.id.recyclerview) RecyclerView recyclerView;
    @BindView(R.id.empty_info) View emptyInfo;
    @BindView(R.id.bluetooth_disabled_container) View bluetoothDisabledContainer;
    @BindView(R.id.bluetooth_enabled_container) View bluetoothEnabledContainer;
    @BindView(R.id.fab) FloatingActionButton fab;

    @BindView(R.id.battery_saving_hint_container) View batterySavingHint;
    @BindView(R.id.battery_saving_hint_dismiss_action) Button batterySavingDismiss;
    @BindView(R.id.battery_saving_hint_show_action) Button batterySavingShow;

    @BindView(R.id.android10_applaunch_hint_container) View appLaunchHint;
    @BindView(R.id.android10_applaunch_hint_dismiss_action) Button appLaunchHintDismiss;
    @BindView(R.id.android10_applaunch_hint_show_action) Button appLaunchHintShow;
    @Inject ManagedDevicesPresenter presenter;

    private Unbinder unbinder;
    private ManagedDevicesAdapter adapter;
    private boolean isProVersion = false;
    private boolean bluetoothEnabled = false;
    private final StateForwarder stateForwarder = new StateForwarder();

    public static Fragment newInstance() {
        return new ManagedDevicesFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        stateForwarder.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.fragment_layout_managed_devices, container, false);
        unbinder = ButterKnife.bind(this, layout);
        return layout;
    }

    @SuppressLint("InlinedApi")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        adapter = new ManagedDevicesAdapter(this);
        recyclerView.setAdapter(adapter);
        ViewCompat.setNestedScrollingEnabled(recyclerView, false);

        bluetoothDisabledContainer.setOnClickListener(v -> presenter.showBluetoothSettingsScreen());

        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        stateForwarder.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        if (unbinder != null) unbinder.unbind();
        super.onDestroyView();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        MVPBakery.<ManagedDevicesPresenter.View, ManagedDevicesPresenter>builder()
                .presenterFactory(new InjectedPresenter<>(this))
                .presenterRetainer(new ViewModelRetainer<>(this))
                .stateForwarder(stateForwarder)
                .addPresenterCallback(new PresenterInjectionCallback<>(this))
                .attach(this);

        super.onActivityCreated(savedInstanceState);
        Check.notNull(getActivity());
        final ActionBar actionBar = ((MainActivity) getActivity()).getSupportActionBar();
        Check.notNull(actionBar);
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setTitle(R.string.app_name);
        actionBar.setSubtitle(null);
    }

    @Override
    public void onCreateOptionsMenu(@NotNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.upgrade).setVisible(!isProVersion);

        Drawable btIcon = DrawableCompat.wrap(menu.findItem(R.id.bluetooth_settings).getIcon());
        int btStateColor = bluetoothEnabled ? android.R.color.white : R.color.state_m3;
        DrawableCompat.setTint(btIcon, ContextCompat.getColor(requireContext(), btStateColor));
        menu.findItem(R.id.bluetooth_settings).setIcon(btIcon);

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
                        .setNegativeButton(R.string.action_cancel, (dialogInterface, i) -> {
                        })
                        .show();
                return true;
            case R.id.bluetooth_settings:
                presenter.showBluetoothSettingsScreen();
                return true;
            case R.id.settings:
                ActivityUtil.tryStartActivity(requireActivity(), new Intent(getContext(), SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @OnClick(R.id.fab)
    void onFabClicked() {
        ActivityUtil.tryStartActivity(this, new Intent(getContext(), BluetoothActivity.class));
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void displayBluetoothState(boolean enabled) {
        bluetoothEnabled = enabled;
        bluetoothDisabledContainer.setVisibility(enabled ? View.GONE : View.VISIBLE);
        bluetoothEnabledContainer.setVisibility(enabled ? View.VISIBLE : View.GONE);
        fab.setVisibility(enabled ? View.VISIBLE : View.GONE);
        requireActivity().invalidateOptionsMenu();
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
    public void onStreamAdjusted(ManagedDevice device, AudioStream.Type type, float percentage) {
        switch (type) {
            case MUSIC:
                presenter.onUpdateMusicVolume(device, percentage);
                break;
            case CALL:
                presenter.onUpdateCallVolume(device, percentage);
                break;
            case RINGTONE:
                presenter.onUpdateRingVolume(device, percentage);
                break;
            case NOTIFICATION:
                presenter.onUpdateNotificationVolume(device, percentage);
                break;
        }
    }

    @Override
    public void onShowConfigScreen(ManagedDevice device) {
        //noinspection ConstantConditions
        getActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.frame_content, ConfigFragment.instantiate(device))
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void displayBatteryOptimizationHint(boolean display, Intent intent) {
        batterySavingDismiss.setOnClickListener(v -> presenter.onBatterySavingDismissed());
        batterySavingShow.setOnClickListener(v -> ActivityUtil.tryStartActivity(this, intent));
        batterySavingHint.setVisibility(display ? View.VISIBLE : View.GONE);
    }

    @Override
    public void displayAndroid10AppLaunchHint(boolean display, Intent intent) {
        appLaunchHintDismiss.setOnClickListener(v -> presenter.onAppLaunchHintDismissed());
        appLaunchHintShow.setOnClickListener(v -> ActivityUtil.tryStartActivity(this, intent));
        appLaunchHint.setVisibility(display ? View.VISIBLE : View.GONE);
    }
}
