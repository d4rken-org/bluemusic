package eu.darken.mvpbakery.injection.broadcastreceiver

import android.content.BroadcastReceiver
import dagger.MapKey
import kotlin.reflect.KClass

@MustBeDocumented
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.RUNTIME)
@MapKey
annotation class BroadcastReceiverKey(val value: KClass<out BroadcastReceiver>)