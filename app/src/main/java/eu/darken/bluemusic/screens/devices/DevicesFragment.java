package eu.darken.bluemusic.screens.devices;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.screens.MainActivity;
import eu.darken.bluemusic.util.Preconditions;
import eu.darken.ommvplib.injection.ComponentPresenterSupportFragment;


public class DevicesFragment extends ComponentPresenterSupportFragment<DevicesPresenter.View, DevicesPresenter, DevicesComponent>
        implements DevicesPresenter.View {

    @BindView(R.id.recyclerview) RecyclerView recyclerView;

    Unbinder unbinder;

    @Override
    public Class<DevicesPresenter> getTypeClazz() {
        return DevicesPresenter.class;
    }

    @Nullable
    @Override
    public android.view.View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        android.view.View layout = inflater.inflate(R.layout.fragment_layout_devicemanager, container, false);
        unbinder = ButterKnife.bind(this, layout);
        return layout;
    }

    @Override
    public void onViewCreated(android.view.View view, @Nullable Bundle savedInstanceState) {
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
        final ActionBar actionBar = ((MainActivity) getActivity()).getSupportActionBar();
        Preconditions.checkNotNull(actionBar);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.label_add_device);
    }

}
