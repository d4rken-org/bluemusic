package eu.darken.bluemusic.bluetooth.core;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import javax.inject.Inject;

import eu.darken.bluemusic.AppComponent;
import eu.darken.bluemusic.main.core.service.ServiceHelper;
import timber.log.Timber;

@AppComponent.Scope
public class EventGenerator {
    public static final String EXTRA_DEVICE_EVENT = "eu.darken.bluemusic.core.bluetooth.event";
    private final Context context;

    @Inject
    public EventGenerator(Context context) {
        this.context = context;
    }

    public SourceDevice.Event generate(SourceDevice device, SourceDevice.Event.Type type) {
        return new SourceDevice.Event(device, type);
    }

    public void send(SourceDevice device, SourceDevice.Event.Type type) {
        Timber.d("Generating and sending %s for %s", type, device);
        SourceDevice.Event event = generate(device, type);

        Intent service = ServiceHelper.getIntent(context);
        service.putExtra(EXTRA_DEVICE_EVENT, event);
        final ComponentName componentName = ServiceHelper.startService(context, service);
        if (componentName != null) Timber.v("Service is already running.");
    }
}
