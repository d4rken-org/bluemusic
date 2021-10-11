package eu.darken.mvpbakery.injection.broadcastreceiver

import android.content.BroadcastReceiver

import dagger.android.AndroidInjector

interface BroadcastReceiverComponent<ReceiverT : BroadcastReceiver> : AndroidInjector<ReceiverT> {
    abstract class Builder<ReceiverT : BroadcastReceiver, ComponentT : BroadcastReceiverComponent<ReceiverT>> : AndroidInjector.Builder<ReceiverT>() {
        abstract override fun build(): ComponentT
    }
}
