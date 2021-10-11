package eu.darken.bluemusic.bluetooth.core

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bugsnag.android.Bugsnag
import dagger.android.HasBroadcastReceiverInjector
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.database.DeviceManager
import eu.darken.bluemusic.main.core.database.ManagedDevice
import eu.darken.bluemusic.main.core.database.RealmSource
import eu.darken.bluemusic.main.core.service.ServiceHelper
import eu.darken.bluemusic.settings.core.Settings
import eu.darken.mvpbakery.injection.broadcastreceiver.HasManualBroadcastReceiverInjector
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject


class BluetoothEventReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_DEVICE_EVENT = "eu.darken.bluemusic.core.bluetooth.event"
        val VALID_ACTIONS = listOf(
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED
        )
    }

    @Inject lateinit var settings: Settings
    @Inject lateinit var realmSource: RealmSource
    @Inject lateinit var streamHelper: StreamHelper
    @Inject lateinit var fakeSpeakerDevice: FakeSpeakerDevice

    @Inject lateinit var deviceManager: DeviceManager

    override fun onReceive(context: Context, intent: Intent) {
        Timber.v("onReceive(%s, %s)", context, intent)

        if (!VALID_ACTIONS.contains(intent.action ?: "")) {
            Timber.e("We got called on an invalid intent: %s", intent)
            return
        }

        // https://stackoverflow.com/questions/46784685
        // https://stackoverflow.com/questions/41061272
        // https://issuetracker.google.com/issues/37137009
        if (context.applicationContext !is HasManualBroadcastReceiverInjector) {
            val ex = RuntimeException(String.format(
                    "%s does not implement %s",
                    context.applicationContext.javaClass.canonicalName,
                    HasBroadcastReceiverInjector::class.java.canonicalName))
            Bugsnag.notify(ex)
            return
        }
        (context.applicationContext as HasManualBroadcastReceiverInjector).broadcastReceiverInjector().inject(this)

        if (!settings.isEnabled) {
            Timber.i("We are disabled.")
            return
        }

        val goAsync = goAsync()

        Single
                .create<SourceDevice.Event> {
                    when (val deviceEvent = SourceDevice.Event.createEvent(intent)) {
                        null -> it.tryOnError(IllegalArgumentException("Couldn't create device event for $intent"))
                        else -> it.onSuccess(deviceEvent)
                    }
                }
                .subscribeOn(Schedulers.io())
                .doOnSuccess { Timber.d("New event: %s", it) }
                .flatMap { event -> return@flatMap deviceManager.devices().firstOrError().map { Pair(event, it) } }
                .doOnSuccess { (_, devices) -> Timber.d("Current devices: %s", devices) }
                .filter { (event, devices) ->
                    val managedDevice = devices[event.address]
                    if (managedDevice == null) Timber.d("Event %s belongs to an un-managed device", event)
                    else Timber.d("Event %s concerns device %s", event, managedDevice)
                    return@filter managedDevice != null
                }
                // If we are changing from speaker to bluetooth this routine tries to save the original volume
                .doOnSuccess { (event, devices) ->
                    if (!settings.isSpeakerAutoSaveEnabled) {
                        Timber.d("Autosave for the device speaker is not enabled.")
                        return@doOnSuccess
                    }
                    if (event.address == FakeSpeakerDevice.ADDR) {
                        return@doOnSuccess
                    }
                    if (event.type !== SourceDevice.Event.Type.CONNECTED) {
                        return@doOnSuccess
                    }

                    val fakeSpeaker = when {
                        devices[FakeSpeakerDevice.ADDR] == null -> {
                            Timber.i("FakeSpeaker device not yet managed, adding.")
                            deviceManager.addNewDevice(fakeSpeakerDevice).blockingGet()
                        }
                        else -> devices[FakeSpeakerDevice.ADDR]!!
                    }

                    // Are we actually replacing the fake speaker device and need to save the volume?
                    val activeDevices = mutableMapOf<String, ManagedDevice>()
                    for ((_, device) in devices) {
                        if (device.isActive) activeDevices[device.address] = device
                    }
                    if (activeDevices.size >= 2 && !activeDevices.containsKey(FakeSpeakerDevice.ADDR)) {
                        Timber.d("Not saving volume, at least 2 Bluetooth devices already connected")
                        return@doOnSuccess
                    } else if (activeDevices.size == 1 && !activeDevices.containsKey(event.address) && !activeDevices.containsKey(FakeSpeakerDevice.ADDR)) {
                        Timber.d("Not saving volume, one Bluetooth device already that isn't the speaker or this.")
                        return@doOnSuccess
                    }

                    val eventDevice = devices[event.address]!!
                    val affectedValues = mutableMapOf<AudioStream.Type, Float>()
                    for (type in AudioStream.Type.values()) {
                        val eventDevTypeVol = eventDevice.getVolume(type) ?: continue

                        val currentVol = streamHelper.getVolumePercentage(fakeSpeaker.getStreamId(type))
                        if (currentVol == eventDevTypeVol) continue
                        affectedValues[type] = currentVol
                    }

                    Timber.d("The connecting device will affect: %s", affectedValues)
                    if (affectedValues.isNotEmpty()) {
                        return@doOnSuccess
                    }

                    for (entry in affectedValues.entries) {
                        fakeSpeaker.setVolume(entry.key, entry.value)
                    }
                    deviceManager.save(Collections.singleton(fakeSpeaker))
                }
                .timeout(8, TimeUnit.SECONDS)
                .doFinally(goAsync::finish)
                .subscribe(
                        { (event, _) ->
                            val service = ServiceHelper.getIntent(context)
                            service.putExtra(EXTRA_DEVICE_EVENT, event)
                            val componentName = ServiceHelper.startService(context, service)
                            if (componentName != null) Timber.v("Service is already running.")
                        },
                        { err ->
                            Timber.e(err, "Failed to process event.")
                        }
                )
    }

}
