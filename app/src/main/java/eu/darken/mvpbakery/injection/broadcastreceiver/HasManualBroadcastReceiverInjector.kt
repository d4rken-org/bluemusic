package eu.darken.mvpbakery.injection.broadcastreceiver


import android.content.BroadcastReceiver

import dagger.android.HasBroadcastReceiverInjector
import eu.darken.mvpbakery.injection.ManualInjector

interface HasManualBroadcastReceiverInjector : HasBroadcastReceiverInjector {
    override fun broadcastReceiverInjector(): ManualInjector<BroadcastReceiver>
}
