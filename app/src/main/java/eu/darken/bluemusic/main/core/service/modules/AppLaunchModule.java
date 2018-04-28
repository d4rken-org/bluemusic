package eu.darken.bluemusic.main.core.service.modules;

import javax.inject.Inject;

import eu.darken.bluemusic.bluetooth.core.SourceDevice;
import eu.darken.bluemusic.main.core.database.ManagedDevice;
import eu.darken.bluemusic.main.core.service.ActionModule;
import eu.darken.bluemusic.main.core.service.BlueMusicServiceComponent;
import eu.darken.bluemusic.util.AppTool;
import timber.log.Timber;

@BlueMusicServiceComponent.Scope
public class AppLaunchModule extends ActionModule {
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
            Timber.e(e);
        }
    }
}
