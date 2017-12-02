package eu.darken.bluemusic.main.core.service.modules;

import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;

import javax.inject.Inject;

import eu.darken.bluemusic.bluetooth.core.SourceDevice;
import eu.darken.bluemusic.main.core.database.ManagedDevice;
import eu.darken.bluemusic.main.core.service.ActionModule;
import eu.darken.bluemusic.main.core.service.BlueMusicServiceComponent;
import eu.darken.bluemusic.settings.core.Settings;
import timber.log.Timber;

@BlueMusicServiceComponent.Scope
public class AutoplayModule extends ActionModule {
    private final AudioManager audioManager;
    private final Settings settings;

    @Inject
    public AutoplayModule(AudioManager audioManager, Settings settings) {
        this.audioManager = audioManager;
        this.settings = settings;
    }

    @Override
    public void handle(ManagedDevice device, SourceDevice.Event event) {
        if (event.getType() != SourceDevice.Event.Type.CONNECTED) return;
        if (!device.isAutoPlayEnabled()) {
            return;
        }
        waitAdjustmentDelay(device);

        final int autoplayKeycode = settings.getAutoplayKeycode();
        Timber.d("Autoplay enabled, sending KeyEvent: %d", autoplayKeycode);

        final long eventTime = SystemClock.uptimeMillis();
        audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, autoplayKeycode, 0));
        audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, autoplayKeycode, 0));
    }
}
