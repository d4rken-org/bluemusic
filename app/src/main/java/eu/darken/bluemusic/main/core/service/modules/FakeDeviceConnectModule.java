package eu.darken.bluemusic.main.core.service.modules;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import eu.darken.bluemusic.bluetooth.core.EventGenerator;
import eu.darken.bluemusic.bluetooth.core.FakeSpeakerDevice;
import eu.darken.bluemusic.bluetooth.core.SourceDevice;
import eu.darken.bluemusic.main.core.database.DeviceManager;
import eu.darken.bluemusic.main.core.database.ManagedDevice;
import eu.darken.bluemusic.main.core.service.ActionModule;
import eu.darken.bluemusic.main.core.service.BlueMusicServiceComponent;
import timber.log.Timber;

@BlueMusicServiceComponent.Scope
public class FakeDeviceConnectModule extends ActionModule {
    private final DeviceManager deviceManager;
    private final EventGenerator eventGenerator;

    @Inject
    FakeDeviceConnectModule(EventGenerator eventGenerator, DeviceManager deviceManager) {
        super();
        this.eventGenerator = eventGenerator;
        this.deviceManager = deviceManager;
    }

    @Override
    public void handle(ManagedDevice eventDevice, SourceDevice.Event event) {
        if (event.getType() != SourceDevice.Event.Type.DISCONNECTED) return;

        final Map<String, ManagedDevice> managed = deviceManager.observe().blockingFirst();
        ManagedDevice fakeSpeaker = managed.get(FakeSpeakerDevice.ADDR);
        Timber.d("FakeSpeaker: %s", fakeSpeaker);
        if (fakeSpeaker == null) return;

        Map<String, ManagedDevice> active = new HashMap<>();
        for (Map.Entry<String, ManagedDevice> entry : managed.entrySet()) {
            if (entry.getValue().isActive()) active.put(entry.getKey(), entry.getValue());
        }
        Timber.d("Active devices: %s", active);
        if (active.size() > 1 || active.size() == 1 && !active.containsKey(fakeSpeaker.getAddress())) return;

        Timber.d("Sending fake device connect event.");
        eventGenerator.send(fakeSpeaker.getSourceDevice(), SourceDevice.Event.Type.CONNECTED);
    }
}
