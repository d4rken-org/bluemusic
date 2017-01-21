package eu.darken.bluemusic.screens.volumes;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import eu.darken.bluemusic.App;
import eu.darken.bluemusic.core.database.DeviceManager;
import eu.darken.bluemusic.core.database.ManagedDevice;
import eu.darken.ommvplib.injection.ComponentPresenter;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@VolumesrScope
public class VolumesPresenter extends ComponentPresenter<VolumesView, VolumesComponent> {
    static final String TAG = App.LOGPREFIX + "IntroPresenter";
    @Inject DeviceManager deviceManager;
    private VolumesView view;
    private Disposable disposable;

    @Inject
    VolumesPresenter() {
    }

    @Override
    public void onCreate(Bundle bundle) {

    }

    @Override
    public void onBindChange(@Nullable VolumesView view) {
        this.view = view;
        if (view != null) {
            disposable = deviceManager.observe()
                    .subscribeOn(Schedulers.computation())
                    .map(managedDevices -> {
                        List<ManagedDevice> sorted = new ArrayList<>(managedDevices.values());
                        Collections.sort(sorted, (d1, d2) -> Long.compare(d1.getLastConnected(), d2.getLastConnected()));
                        return sorted;
                    })
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(view::displayDevices);
        } else {
            if (disposable != null) disposable.dispose();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle bundle) {

    }

    @Override
    public void onDestroy() {

    }

    public void updateDeviceVolume(ManagedDevice managedDevice, float percent) {

    }

    public void removeDevice(ManagedDevice managedDevice) {

    }
}
