package eu.darken.bluemusic.core.bluetooth;


import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dagger.Subcomponent;
import eu.darken.ommvplib.injection.broadcastreceiver.BroadcastReceiverComponent;

@BootCheckReceiverComponent.Scope
@Subcomponent()
public interface BootCheckReceiverComponent extends BroadcastReceiverComponent<BootCheckReceiver> {

    @Subcomponent.Builder
    abstract class Builder extends BroadcastReceiverComponent.Builder<BootCheckReceiver, BootCheckReceiverComponent> {

    }

    @javax.inject.Scope
    @Retention(RetentionPolicy.RUNTIME)
    @interface Scope {
    }
}
