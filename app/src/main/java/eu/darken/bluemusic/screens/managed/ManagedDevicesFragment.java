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
import eu.darken.bluemusic.core.Settings;
import eu.darken.bluemusic.core.database.ManagedDevice;
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
    @BindView(R.id.fab) FloatingActionButton fab;

    Unbinder unbinder;
    private ManagedDevicesAdapter adapter;

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
        android.view.View layout = inflater.inflate(R.layout.fragment_layout_volumemanager, container, false);
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
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
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
    public void displayDevices(List<ManagedDevice> managedDevices) {
        adapter.setData(managedDevices);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onDeleteDevice(ManagedDevice device) {
        getPresenter().deleteDevice(device);
    }

    @SuppressLint("InflateParams")
    @Override
    public void onEditDelay(ManagedDevice device) {
        View container = getLayoutInflater().inflate(R.layout.view_dialog_delay, null);
        EditText input = container.findViewById(R.id.input);
        input.setText(device.getActionDelay() != null ? String.valueOf(device.getActionDelay()) : String.valueOf(Settings.DEFAULT_DELAY));
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.action_edit_delay)
                .setMessage(R.string.msg_action_delay)
                .setIcon(R.drawable.ic_av_timer_white_24dp)
                .setView(container)
                .setPositiveButton(R.string.action_set, (dialogInterface, i) -> {
                    try {
                        final long delay = Long.parseLong(input.getText().toString());
                        getPresenter().editDelay(device, delay);
                    } catch (NumberFormatException e) {
                        Timber.e(e);
                        Preconditions.checkNotNull(getView());
                        Snackbar.make(getView(), R.string.msg_invalid_input, Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton(R.string.action_reset, (dialogInterface, i) -> getPresenter().editDelay(device, 0))
                .setNegativeButton(R.string.action_cancel, (dialogInterface, i) -> {
                })
                .show();
    }

    @Override
    public void onMusicVolumeAdjusted(ManagedDevice device, float percentage) {
        getPresenter().updateMusicVolume(device, percentage);
    }

    @Override
    public void onCallVolumeAdjusted(ManagedDevice device, float percentage) {
        getPresenter().updateCallVolume(device, percentage);
    }

    @Override
    public void onToggleMusicVolumeAction(ManagedDevice device) {
        getPresenter().toggleMusicVolumeAction(device);
    }

    @Override
    public void onToggleCallVolumeAction(ManagedDevice device) {
        getPresenter().toggleCallVolumeAction(device);
    }
}
