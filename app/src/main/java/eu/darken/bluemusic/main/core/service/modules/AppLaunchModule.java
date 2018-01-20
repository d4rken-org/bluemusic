package eu.darken.bluemusic.main.core.service.modules;

import javax.inject.Inject;

import eu.darken.bluemusic.bluetooth.core.SourceDevice;
import eu.darken.bluemusic.main.core.database.DeviceManager;
import eu.darken.bluemusic.main.core.database.ManagedDevice;
import eu.darken.bluemusic.main.core.service.ActionModule;
import eu.darken.bluemusic.main.core.service.BlueMusicServiceComponent;
import eu.darken.bluemusic.util.AppTool;

@BlueMusicServiceComponent.Scope
public class AppLaunchModule extends ActionModule {
    private final AppTool appTool;
    private final DeviceManager deviceManager;

    @Inject
    AppLaunchModule(AppTool appTool, DeviceManager deviceManager) {
        super();
        this.appTool = appTool;
        this.deviceManager = deviceManager;
    }

    @Override
    public void handle(ManagedDevice eventDevice, SourceDevice.Event event) {
        if (event.getType() != SourceDevice.Event.Type.CONNECTED) return;
        if (eventDevice.getLaunchPkg() == null) return;
        appTool.launch(eventDevice.getLaunchPkg());
    }
}
