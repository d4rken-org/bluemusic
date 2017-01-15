package eu.darken.bluemusic.screens.volumes;

import java.util.List;

import eu.darken.bluemusic.core.database.ManagedDevice;
import eu.darken.ommvplib.Presenter;

public interface VolumeManagerView extends Presenter.View {

    void displayDevices(List<ManagedDevice> managedDevices);

}
