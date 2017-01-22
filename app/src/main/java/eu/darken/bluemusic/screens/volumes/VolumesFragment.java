package eu.darken.bluemusic.screens.volumes;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.core.database.ManagedDevice;
import eu.darken.bluemusic.screens.MainActivity;
import eu.darken.bluemusic.screens.about.AboutFragment;
import eu.darken.bluemusic.screens.settings.SettingsFragment;
import eu.darken.ommvplib.injection.ComponentPresenterFragment;


public class VolumesFragment extends ComponentPresenterFragment<VolumesView, VolumesPresenter, VolumesComponent>
        implements VolumesView, VolumesAdapter.Callback {

    @BindView(R.id.recyclerview) RecyclerView recyclerView;

    Unbinder unbinder;

    public static android.support.v4.app.Fragment newInstance() {
        return new VolumesFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void inject(@NonNull VolumesComponent component) {

    }

    @Override
    public VolumesComponent createComponent() {
        VolumesComponent.Builder builder = getComponentBuilder(this);
        return builder.build();
    }

    @Override
    public Class<VolumesPresenter> getTypeClazz() {
        return VolumesPresenter.class;
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
    public void onDestroyView() {
        if (unbinder != null) unbinder.unbind();
        super.onDestroyView();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        //noinspection ConstantConditions
        ((MainActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(false);
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

    @Override
    public void displayDevices(List<ManagedDevice> managedDevices) {
        recyclerView.setAdapter(new VolumesAdapter(managedDevices, this));
    }

    @Override
    public void onMusicVolumeAdjusted(ManagedDevice device, float percentage) {
        presenter.updateMusicVolume(device, percentage);
    }

    @Override
    public void onVoiceVolumeAdjusted(ManagedDevice device, float percentage) {
        presenter.updateVoiceVolume(device, percentage);
    }
}
