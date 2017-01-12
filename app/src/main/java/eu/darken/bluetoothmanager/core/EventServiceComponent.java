package eu.darken.bluetoothmanager.core;

import dagger.Component;
import eu.darken.bluetoothmanager.AppComponent;
import eu.darken.bluetoothmanager.util.dagger.ServiceScope;


@ServiceScope
@Component(dependencies = AppComponent.class)
public interface EventServiceComponent {
    void inject(EventService eventService);
}
