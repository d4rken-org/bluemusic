package eu.darken.bluemusic.bluetooth.core;


import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dagger.Subcomponent;
import eu.darken.mvpbakery.injection.broadcastreceiver.BroadcastReceiverComponent;

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
