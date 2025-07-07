package eu.darken.bluemusic.main.core.service.modules.events;

import javax.inject.Inject;

import eu.darken.bluemusic.AppComponent;
import eu.darken.bluemusic.bluetooth.core.SourceDevice;
import eu.darken.bluemusic.data.device.ManagedDevice;
import eu.darken.bluemusic.main.core.service.modules.EventModule;
import eu.darken.bluemusic.util.AppTool;
import timber.log.Timber;

@AppComponent.Scope
public class AppLaunchModule extends EventModule {
    private final AppTool appTool;

    @Inject
    AppLaunchModule(AppTool appTool) {
        super();
        this.appTool = appTool;
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public void handle(ManagedDevice eventDevice, SourceDevice.Event event) {
        if (event.getType() != SourceDevice.Event.Type.CONNECTED) return;
        if (eventDevice.getLaunchPkg() == null) return;

        Timber.d("Launching app %s", eventDevice.getLaunchPkg());
        appTool.launch(eventDevice.getLaunchPkg());

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Timber.w(e);
        }
    }
}
