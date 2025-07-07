package eu.darken.bluemusic.main.core.service.modules.events

import eu.darken.bluemusic.AppComponent
import eu.darken.bluemusic.bluetooth.core.FakeSpeakerDevice
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.SourceDevice.Event.Type.CONNECTED
import eu.darken.bluemusic.bluetooth.core.SourceDevice.Event.Type.DISCONNECTED
import eu.darken.bluemusic.data.device.DeviceManagerFlow
import eu.darken.bluemusic.data.device.ManagedDevice
import eu.darken.bluemusic.main.core.service.modules.EventModule
import eu.darken.bluemusic.util.WakelockMan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

@AppComponent.Scope
class KeepAwakeModule @Inject internal constructor(
    private val wakelockMan: WakelockMan, private val deviceManager: DeviceManagerFlow
) : EventModule() {

    override fun getPriority(): Int = 1

    override fun handle(device: ManagedDevice, event: SourceDevice.Event) {
        if (!device.keepAwake) return
        if (device.address == FakeSpeakerDevice.ADDR) {
            Timber.e("Keep awake should not be enabled for the fake speaker device: %s", device)
            return
        }

        val deviceMap = runBlocking { deviceManager.devices().first() }
        val otherWokeDevice = deviceMap.values.find { d -> d.keepAwake && d.address != event.address }

        when (event.type) {
            CONNECTED -> {
                Timber.d("Acquiring wakelock for %s", device)
                wakelockMan.tryAquire()
            }
            DISCONNECTED -> {
                if (otherWokeDevice == null) {
                    Timber.d("Releasing wakelock for %s", device)
                    wakelockMan.tryRelease()
                } else {
                    Timber.i("Not releasing wakelock, another device also wants 'keep awake': %s", otherWokeDevice)
                }
            }
        }
    }
}
