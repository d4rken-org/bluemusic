package eu.darken.bluetoothmanager.screens.newdevice;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;

import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import eu.darken.bluetoothmanager.App;
import eu.darken.bluetoothmanager.R;
import eu.darken.bluetoothmanager.util.mvp.BasePresenterActivity;
import eu.darken.bluetoothmanager.util.mvp.PresenterFactory;


public class NewDeviceActivity extends BasePresenterActivity<NewDeviceContract.Presenter, NewDeviceContract.View>
        implements NewDeviceContract.View, NewDeviceAdapter.Callback {
    static final String TAG = App.prefixTag("NewDeviceActivity");
    @BindView(R.id.recyclerview) RecyclerView recyclerView;
    @BindView(R.id.toolbar) Toolbar toolbar;
    @Inject PresenterFactory<NewDeviceContract.Presenter> presenterFactory;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        DaggerNewDeviceComponent.builder()
                .newDeviceModule(new NewDeviceModule())
                .appComponent(App.Injector.INSTANCE.getAppComponent())
                .build().inject(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_new_device);
        ButterKnife.bind(this);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    @NonNull
    @Override
    protected PresenterFactory<NewDeviceContract.Presenter> getPresenterFactory() {
        return presenterFactory;
    }

    @Override
    public void showDevices(List<BluetoothDevice> devices) {
        recyclerView.setAdapter(new NewDeviceAdapter(devices, this));
    }

    @Override
    public void finishView() {
        finish();
    }

    @Override
    public void onDeviceSelected(BluetoothDevice bluetoothDevice) {
        getPresenter().addDevice(bluetoothDevice);
    }
}
