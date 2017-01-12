package eu.darken.bluetoothmanager.screens.volumemanager;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
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
import eu.darken.bluetoothmanager.App;
import eu.darken.bluetoothmanager.R;
import eu.darken.bluetoothmanager.core.device.ManagedDevice;
import eu.darken.bluetoothmanager.screens.MainActivity;
import eu.darken.bluetoothmanager.screens.newdevice.NewDeviceActivity;
import eu.darken.bluetoothmanager.util.mvp.BasePresenterFragment;
import eu.darken.bluetoothmanager.util.mvp.PresenterFactory;


public class VolumeManagerFragment extends BasePresenterFragment<VolumeManagerContract.Presenter, VolumeManagerContract.View> implements
        VolumeManagerContract.View,
        VolumeManagerAdapter.Callback {

    @BindView(R.id.toolbar) Toolbar toolbar;
    @BindView(R.id.fab) FloatingActionButton fab;
    @BindView(R.id.recyclerview) RecyclerView recyclerView;
    @Inject PresenterFactory<VolumeManagerContract.Presenter> presenterFactory;

    Unbinder unbinder;

    public static Fragment newInstance() {
        return new VolumeManagerFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        DaggerVolumeManagerComponent.builder()
                .appComponent(App.Injector.INSTANCE.getAppComponent())
                .build().inject(this);
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.fragment_layout_volumemanager, container, false);
        unbinder = ButterKnife.bind(this, layout);
        return layout;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        ((MainActivity) getActivity()).setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_volume_up_white_24dp);
    }

    @OnClick(R.id.fab)
    public void onFabClicked(View view) {
        startActivity(new Intent(getContext(), NewDeviceActivity.class));
    }

    @Override
    public void onPresenterReady(@NonNull VolumeManagerContract.Presenter presenter) {
        super.onPresenterReady(presenter);
    }

    @NonNull
    @Override
    protected PresenterFactory<VolumeManagerContract.Presenter> getPresenterFactory() {
        return presenterFactory;
    }

    @Override
    public void onDestroyView() {
        if (unbinder != null) unbinder.unbind();
        super.onDestroyView();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_menu_intro, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void displayDevices(List<ManagedDevice> managedDevices) {
        recyclerView.setAdapter(new VolumeManagerAdapter(managedDevices, this));
    }

}
